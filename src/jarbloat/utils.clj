(ns jarbloat.utils
  (:require [clojure.string :as s]))

(defmacro if-graalvm
  "Execute blocks if graalvm is detected."
  ([then else]
   ;; "graalvm.compiler" is set through leiningen profile during compilation, but
   ;; "com.oracle.graalvm.isaot" can be emitted by graal in some versions AFAIK
   (if (or (System/getProperty "graalvm.compiler")
           (System/getProperty "com.oracle.graalvm.isaot"))
     `(do ~then)
     `(do ~else)))
  ([then] `(if-graalvm ~then nil)))

(defn path-drop-last
  "Drop last item in path and re-create path by using join-token."
  ([^String path join-token]
   (assert (and path (.contains path "/")) "Invalid path string")
   (->> (.split ^String path "/") butlast (s/join join-token)))
  ([path] (path-drop-last path "/")))
