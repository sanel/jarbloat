(ns jarbloat.class-analyzer
  (:require [clojure.repl :refer [demunge]]
            [clojure.string :as s]
            [clojure.java.io :as io])
  (:import [org.apache.bcel.classfile ClassParser JavaClass]
           java.io.InputStream))

(defprotocol ClassAnalyzer
  (analyzer-name [_] "Get analyzer name")
  (load-class    [_ path] "Create a class object for this analyzer")
  (get-package   [_ cls] "Get package name from the given .class path")
  (get-classname [_ cls opts] "Get readable class name from the given .class path"))

;; try to determine metadata based solely on class path
(deftype FastAnalyzer []
  ClassAnalyzer
  (analyzer-name [_] "fast")

  (load-class [_ path] path)

  (get-package [_ cls]
    (let [^String name cls]
      (->> (.split name "/") butlast (s/join "."))))

  (get-classname [this cls {:keys [demunge?]}]
    (let [^String name cls
          ^String name (-> name (.split "/") last)
          name (.replaceAll name "\\.class$" "")]
      (str
       ;; return package.name like BCELAnalyzer will do
       (get-package this cls) "." (if demunge?
                                    (demunge name)
                                    name)))))

;; use Apache BCEL (https://commons.apache.org/proper/commons-bcel/) for analysis.
;; Slower, because it will load bytecode and parse it, but it's more accurate.
(deftype BCELAnalyzer []
  ClassAnalyzer
  (analyzer-name [_] "bcel")

  (load-class [_ path]
    (assert (or (string? path)
                (and
                 (sequential? path)
                 (= 2 (count path))))
            "only string or list accepted as a path")
    (let [^InputStream stream (if (string? path) (io/input-stream path) (first path))
          ^String p           (if (string? path) path (second path))]
      (try
        (.parse (ClassParser. stream p))
        (finally
          ;; do not close this stream if is not ours
          (when-not (string? path)
            (and stream (.close stream)))))))

  (get-package [_ cls]
    (.getPackageName ^JavaClass cls))

  (get-classname [_ cls _]
    (.getClassName ^JavaClass cls)))

(comment

(let [o (->FastAnalyzer)
      ;o (->BCELAnalyzer)
      ;path "target/classes/jarbloat/analyzer$group_by_ns$fn__46$fn__50.class"
      path "target/classes/jarbloat/ca/FastAnalyzer.class"
      c (load-class o path)
      ;c (load-class o [(io/input-stream path) path])
      ]
  (println "anal   : " (analyzer-name o))
  (println "package: " (get-package o c))
  (println "name   : " (get-classname o c {:demunge true})))

)
