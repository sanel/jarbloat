(ns jarbloat.printer
  (:require [clojure.pprint :as pp]
            [clojure.string :refer [join]]
            [cheshire.core :as json]))

(defn- print-csv [ks rows]
  (doseq [r rows]
    (println (->> (select-keys r ks) vals (join ",")))))

;; huge graphviz images
;; https://stackoverflow.com/questions/13417411/laying-out-a-large-graph-with-graphviz

(defn do-print-dot
  "Print GraphViz dot format to stdout, assuming rows are
obtained through (analyze-entry-deps)."
  [rows]
  (println "
digraph deps_graph {
  fontname=\"Helvetica,Arial,sans-serif\";
  node [fontsize=10, shape=box, height=0.25];
  node [shape=box];
  edge [fontsize=10];
  rankdir=\"LR\";")
  (doseq [r rows]
    (doseq [d (:deps r)]
      ;; do not write if it depends on itself
      (when-not (= (:name r) d)
        (printf "  \"%s\" -> \"%s\";\n" (:name r) d))))
  (println "}"))

(defn do-print
  "Print content depending on print type."
  [type ks rows]
  (case type
    :json (println
           (json/generate-string (map #(select-keys % ks) rows)
                                 {:pretty true}))
    :csv (print-csv ks rows)
    (pp/print-table ks rows)))
