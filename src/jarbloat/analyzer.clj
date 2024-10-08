(ns jarbloat.analyzer
  (:require [jarbloat.utils :refer [path-cut pp-bytes update-with-keys if-graalvm]]
            [jarbloat.printer :as p]
            [jarbloat.class-analyzer :as c]
            [clojure.java.io :as io])
  (:import [java.util.jar JarFile JarEntry]
           [java.io PushbackReader InputStream InputStreamReader File]))

(defn- percentage
  "Calculage percentage"
  [part total as-string?]
  (let [n (float (* 100 (/ part total)))]
    (if as-string?
      (format "%.2f%%" n)
      n)))

(defn- clojure-parse-ns
  "Try to parse clojure namespace from input stream."
  [^InputStream s]
  (with-open [rdr (-> s InputStreamReader. PushbackReader.)]
    (try
      ;; FIXME: consider using clojure.tools.reader for this
      (when-let [form (loop []
                        (let [form (read rdr)]
                          (cond
                            ;; (ns ...)
                            (and (list? form) (= 'ns (first form)))
                            form
                            ;; (comment ...)
                            (and (list? form) (= 'comment (first form)))
                            (recur)
                            ;; nothing to know about
                            :else nil)))]
        (-> form second str))
      (catch Exception _))))

(defn- group-by-ns
  "Emit entries grouped by namespace but also with size/csize summed for that namespace/package."
  [entries jarsz]
  (let [accum (fn [mps k] (reduce (fn [n e] (+ n (get e k))) 0 mps))]
    (map (fn [[k mps]]
           (let [total-csize (accum mps :csize)]
             {:package k
              :size    (accum mps :size)
              :csize   total-csize
              ;; Percent is calculated comparing sum of compressed sizes against
              ;; total jarsize. It doesn't make sense to use uncompressed size for percentage
              ;; because it doesn't tell actuall jar size.
              :percent (percentage total-csize jarsz true)}))
         (group-by :package entries))))

(defn- group-by-ns-for-deps
  "Similar to group-by-ns, but only for dot/deps output."
  [entries]
  (let [accum (fn [mps k] (reduce #(apply conj %1 (get %2 k)) #{} mps))]
    (map (fn [[k mps]]
           {:name k
            :deps (accum mps :deps)})
         (group-by :name entries))))


#_(defn- tree-by-ns
  "Similar to (group-by-ns), but emit a tree where parent node contains metadata like total
size of children packages and each child node will contain child classes and their size.
This is used for html output."
  [entries jarsz]
  (let [accum (fn [mps k] (reduce (fn [n e] (+ n (get e k))) 0 mps))]
    ;; Drop entry with k and replace it with k with metadata. Something
    ;; like this: {k [{:package k :size x ..} {}]} to {{:package k :size x} [...]}
    (map (fn [[k mps]]
           ;; find metadata of k in mps
           (if-let [meta (first (filter #(= k (:package %)) mps))]
             ;; now remove all k metadata from ps
             (let [mps (remove #(= k (:name %)) mps)]
               (let [total-csize (accum mps :scize)
                     meta       {:name    k
                                 :size    (accum mps :size)
                                 :csize   total-csize
                                 :percent (percentage total-csize jarsz true)
                                 :nfiles  (count mps)
                                 :type    :package}]
                {meta mps}))))
         (group-by :package entries))))

(defn- analyze-entry
  "Analyze single entry from JarFile. Returns nil if can't be analyzed."
  [^JarFile fd ^JarEntry e class-analyzer jarsz opts]
  (when (not (.isDirectory e))
    (let [path (.getName e)
          sz   (.getSize e)]
      (merge
       ;; default values; will be overridden by matched cond blocks.
       {:name path
        :path path
        :package (path-cut path)
        :size sz
        :csize (.getCompressedSize e)
        :percent (percentage sz jarsz true)
        :type :file}
       (cond
         ;; java .class files
         (.endsWith path ".class")
         (let [stream (when (= "bcel" (c/analyzer-name class-analyzer))
                        (.getInputStream fd e))
               cls  (c/load-class class-analyzer (if stream
                                                   [stream path]
                                                   path))]
           {:name    (c/get-classname class-analyzer cls {:demunge? (:demunge opts)})
            :package (c/get-package class-analyzer cls)
            :type    :class})

         ;; clojure source files
         (re-find #"\.clj[sxc]?$" path)
         (when-let [ns (clojure-parse-ns (.getInputStream fd e))]
           {:package ns
            :type    :clojure})

         :else nil)))))

(defn- analyze-entry-deps
  "Analyze JarEntry class dependencies. Skip any other entries.
Returns nil on skipped entries or a map with class name and dependencies."
  [^JarFile fd ^JarEntry e class-analyzer _ opts]
  (assert (= "bcel" (c/analyzer-name class-analyzer))
          "Analyzing class dependencies works only with BCEL")
  (when (not (.isDirectory e))
    (let [path (.getName e)]
      (when (.endsWith path ".class")
        (let [stream (.getInputStream fd e)
              cls    (c/load-class class-analyzer (if stream
                                                    [stream path]
                                                    path))]
          (if (:group-ns opts)
            (when-let [;; package can be nil if .class does not have any. For example
                       ;; module-info.class can be without packages
                       package (->> cls (c/get-package class-analyzer) not-empty)]
              {:name package
               :deps (map #(path-cut % #"\." ".") (c/get-deps class-analyzer cls))})
            {:name    (c/get-classname class-analyzer cls {:demunge? (:demunge opts)})
             :package (c/get-package class-analyzer cls)
             :deps    (c/get-deps class-analyzer cls)}))))))

(defn package-filter
  "Check if (:package mp) matches one of include-re regexes. If matches, returns mp
so it can be chained further. If exclude-re is present, check if does not match to
one of those.

The logic here is the following:

 1. if include-re is present, package name must matche one of regexes
 2. if exclude-re is present, package name must not match one of regexes
 3. if package name matches both include-re and exclude-re, use include-re
which means it should be shown."
  [mp include-re exclude-re]
  (assert (and (or (nil? include-re) (sequential? include-re))
               (or (nil? exclude-re) (sequential? exclude-re)))
          "include-re and exclude-re must be either a list of regexes or nil")

  (if-let [package (or (:package mp)
                       ;; group-ns will not set :package but :name
                       (:name mp))]
    (cond
      (seq (not-empty include-re))
      (when (some #(re-find % package) include-re)
        mp)

      (seq (not-empty exclude-re))
      (when (some #(not (re-find % package)) exclude-re)
        mp)

      :else mp)
    mp))

(defn- setup-output
  "If output was given, return it as is. If dir was given, get basename from
path, create dir and return dir/path-basename.output-type. If none given, return *out*."
  [^String path dir output type]
  (cond
    dir
    (let [ext (if (or (nil? type) (= type "table")) "txt" type)
          d (doto (io/file dir)
              (.mkdirs))
          basename (last (.split path File/separator))]
      (io/writer (io/file d (str basename "." ext))))

    output
    (-> output io/file io/writer)

    :else *out*))

(defn analyze-jar
  "Run jar analysis with the given options."
  [^String path opts]
  (let [an (if (or (= "bcel" (:analyzer opts))
                   ;; calculating class dependencies works only with BCEL backend
                   (:deps opts))
             (if-graalvm
               ;; BCEL analyzer will throw NullPointerException when compiled with GraalVM
               (throw (Exception.
                       (str "BCEL analyzer does not work properly with GraalVM (native build).\n"
                            "For now, please use 'java -jar jarbloat.jar' instead. See https://github.com/sanel/jarbloat/issues/2\n")))
               (c/->BCELAnalyzer))
             (c/->FastAnalyzer))
        sz (-> path File. .length)]
    (try
      (binding [*out* (setup-output path (:output-dir opts) (:output opts) (:output-type opts))]
        (with-open [fd (JarFile. path)]
          (let [entry-analyzer (if (:deps opts)
                                 analyze-entry-deps
                                 analyze-entry)
                entries (->> (map #(-> (entry-analyzer fd ^JarEntry % an sz opts)
                                       (package-filter (:include-ns opts) (:exclude-ns opts)))
                                  (-> fd .entries enumeration-seq))
                             (remove nil?))]
            (if (:deps opts)
              (let [entries (if (:group-ns opts)
                              (group-by-ns-for-deps entries)
                              entries)]
                (p/do-print-dot entries))
              (let [comparator (if (:sort-asc opts)
                                 #(compare %1 %2)
                                 #(compare %2 %1))
                    sort-key (case (:sort opts)
                               "name"    :name
                               "package" :package
                               "ns"      :package
                               "csize"   :csize
                               ;; default is sort by uncompressed size
                               :size)
                    sort-key (if (and (:group-ns opts) (= sort-key :name))
                               (do
                                 (println
                                  (str "*** I can't sort by 'name' because class names are not visible when"
                                       " grouped by package. I'm going to use '--sort=package' instead."))
                                 :package)
                               sort-key)
                    ;; When we want to pretty-print sizes, first make sure to sort the content
                    ;; by size key (:size or :csize), then replace entries with updated sizes.
                    ;; This way we don't have to hold in every map additional pretty-print valies.
                    sort-entries (if (:pp-sizes opts)
                                   (fn [k c e]
                                     (map #(update-with-keys % [:size :csize] pp-bytes)
                                          (sort-by k c e)))
                                   sort-by)
                    output-type (case (:output-type opts)
                                  "csv"  :csv
                                  "json" :json
                                  "html" :html
                                  :table)]
                #_(tree-by-ns entries sz)
                (if (:group-ns opts)
                  (p/do-print path
                              output-type
                              [:package :percent :size :csize]
                              (sort-entries sort-key comparator (group-by-ns entries sz)))
                  (p/do-print path
                              output-type
                              [:name :package :percent :size :csize :type]
                              (sort-entries sort-key comparator entries))))))))
      (catch Exception e
        (printf "Error loading %s: %s\n" path (.getMessage e))
        (flush)))))

(defmacro analyze-jars
  "Analyze multiple jars with the given options."
  [paths opts]
  `(doseq [f# ~paths] (analyze-jar f# ~opts)))
