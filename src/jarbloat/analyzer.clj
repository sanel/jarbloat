(ns jarbloat.analyzer
  (:require [jarbloat.utils :refer [path-drop-last pp-bytes update-keys]]
            [jarbloat.printer :as p]
            [jarbloat.class-analyzer :as c]
            [cheshire.core :as json])
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
  "Emit entries grouped by namespace but also with size/csize summed for that namespace."
  [entries jarsz]
  (let [accum (fn [mps k] (reduce (fn [n e] (+ n (get e k))) 0 mps))]
    (map (fn [[k mps]]
           (let [total-size (accum mps :size)]
             {:package k
              :size    total-size
              :csize   (accum mps :csize)
              :percent (percentage total-size jarsz true)}))
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
        :package (path-drop-last path)
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

(defn analyze-jar
  "Run jar analysis with the given options."
  [^String path opts]
  (let [an (if (= "bcel" (:analyzer opts))
             (c/->BCELAnalyzer)
             (c/->FastAnalyzer))
        sz (-> path File. .length)]
    (try
      (with-open [fd (JarFile. path)]
        (let [entries (->> (map #(analyze-entry fd ^JarEntry % an sz opts)
                                (-> fd .entries enumeration-seq))
                           (remove nil?))
              comparator (if (:sort-asc opts)
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
                               (map #(update-keys % [:size :csize] pp-bytes)
                                    (sort-by k c e)))
                             sort-by)
              output-type (case (:output-type opts)
                            "csv"  :csv
                            "json" :json
                            "html" :html
                            :table)]
          (if (:group-ns opts)
            (p/do-print output-type
                        [:package :percent :size :csize]
                        (sort-entries sort-key comparator (group-by-ns entries sz)))
            (p/do-print output-type
                        [:name :package :percent :size :csize :type]
                        (sort-entries sort-key comparator entries)))))
      (catch Exception e
        (printf "Error loading %s: %s\n" path (.getMessage e))
        (flush)))))

(defmacro analyze-jars
  "Analyze multiple jars with the given options."
  [paths opts]
  `(doseq [f# ~paths] (analyze-jar f# ~opts)))
