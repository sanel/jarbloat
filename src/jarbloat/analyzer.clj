(ns jarbloat.analyzer
  (:require [clojure.pprint :as pp]
            [clojure.string :as s]
            [jarbloat.class-analyzer :as c])
  (:import [java.util.jar JarFile JarEntry]
           [java.io PushbackReader InputStream InputStreamReader]))

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
  [entries]
  (let [accum (fn [mps k] (reduce (fn [n e] (+ n (get e k))) 0 mps))]
    (map (fn [[k mps]]
           {:package k
            :size  (accum mps :size)
            :csize (accum mps :csize)})
         (group-by :package entries))))

(defn- analyze-entry
  "Analyze single entry from JarFile. Returns nil if can't be analyzed."
  [^JarFile fd ^JarEntry e class-analyzer opts]
  (when (not (.isDirectory e))
    (let [path (.getName e)]
      (merge
       {:name path
        :path path
        :package (s/join "/" (-> path (.split "/") butlast))
        :size (.getSize e)
        :csize (.getCompressedSize e)
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
             (c/->FastAnalyzer))]
    (try
      (with-open [fd (JarFile. path)]
        (let [entries (->> (map #(analyze-entry fd ^JarEntry % an opts)
                                (-> fd .entries enumeration-seq))
                           (remove nil?))]
          (if (:group-ns opts)
            (pp/print-table (sort-by :size #(compare %2 %1) (group-by-ns entries)))
            (pp/print-table [:name :package :size :csize :type] (sort-by :name entries)))

          ;(pp/print-table (sort-by :size #(compare %2 %1) (group-by-ns entries)))
          ;(pp/print-table [:name :package :size :csize :type] (sort-by :size #(compare %2 %1) entries))
          ))
      (catch Exception e
        (printf "Error loading %s: %s\n" path (.getMessage e))
        (flush)))))

(defmacro analyze-jars
  "Analyze multiple jars with the given options."
  [paths opts]
  `(doseq [f# ~paths] (analyze-jar f# ~opts)))
