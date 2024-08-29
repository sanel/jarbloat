(ns jarbloat.analyzer
  (:require [clojure.repl :refer [demunge]]
            [clojure.string :as s]
            [clojure.pprint :as pp])
  (:import [java.util.jar JarFile JarEntry]))

(defn- classfile-to-readable [^String file demunge?]
  (let [s (.replaceAll file "\\.class$" "")
        ;; do not demunge non-class files
        s (if (and demunge? (.endsWith file ".class"))
            (demunge s)
            s)]
    (s/join "." (.split ^String s "/"))))

(defn- get-namespace
  "Try to extract namespace from filename if ends with .class, .java or .clj
Returns nil if could not extract namespace."
  [^String file]
  (let [pat #"\.(java|clj|class)$"]
    (when (re-find pat file)
      (let [^String s (s/replace file pat "")
            ^String s (demunge s)]
        (->> (.split s "/+|--") butlast (s/join "."))))))

(defn- group-by-ns
  "Emit entries grouped by namespace but also with size/csize summed for that namespace."
  [entries]
  (map (fn [[k mps]]
         {:ns k
          :size  (reduce (fn [n e] (+ n (:size e))) 0 mps)
          :csize (reduce (fn [n e] (+ n (:csize e))) 0 mps)})
       (group-by :ns entries)))

(defn analyze-jar
  "Run jar analysis with the given options."
  [^String path opts]
  (try
    (with-open [fd (JarFile. path)]
      (let [demunge?    (:demunge opts)
            entries (->> (map (fn [^JarEntry e]
                                (when-not (.isDirectory e)
                                  {:name  (classfile-to-readable (.getName e) demunge?)
                                        ;:name  (.getName e)
                                   :size  (.getSize e)
                                   :csize (.getCompressedSize e)
                                   :ns    (get-namespace (.getName e))}))
                              (-> fd .entries enumeration-seq))
                         (remove nil?))
            entries (sort-by :size #(compare %2 %1) entries)]
        (println path)
        (pp/print-table (sort-by :size #(compare %2 %1) (group-by-ns entries)))))
        ;#_(pp/print-table [:name :ns :size :csize] entries)
        ;#_(doseq [x entries]
        ;    (println x))))
    (catch Exception e
      (printf "Error loading %s: %s\n" path (.getMessage e))
      (flush))))

(defmacro analyze-jars
  "Analyze multiple jars with the given options."
  [paths opts]
  `(doseq [f# ~paths] (analyze-jar f# ~opts)))
