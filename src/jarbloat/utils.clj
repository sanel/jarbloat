(ns jarbloat.utils)

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
