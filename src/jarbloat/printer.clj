(ns jarbloat.printer
  (:require [clojure.pprint :as pp]
            [cheshire.core :as json]))

(defn do-print
  "Print content depending on print type."
  [type ks rows]
  (pp/print-table ks rows))
