(defproject jarbloat "0.1.0-SNAPSHOT"
  :description "Analyzie Java JAR files"
  :url "https://github.com/sanel/jarbloat"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.apache.bcel/bcel "6.10.0"]
                 [net.sf.jopt-simple/jopt-simple "6.0-alpha-3"]]
  :global-vars {*warn-on-reflection* true}
  :repl-options {:init-ns jarbloat.core, :port 7888}
  :uberjar-name "jarbloat.jar"
  :jvm-opts ["-Dclojure.main.report=stderr"
             "-Xverify:none"]
  :aliases {"lint" ["with-profile" "lint" "run" "-m" "clj-kondo.main" "--lint" "src"]}
  :profiles {:uberjar {:aot :all
                       :omit-source true
                       :javac-options ["-g:none" "-Xlint:-options"]
                       :jvm-opts ["-Dclojure.compiler.elide-meta=[:doc :file :line :added]"
                                  "-Dclojure.compiler.direct-linking=true"]}
             :graalvm {:jvm-opts ["-Dgraalvm.compiler=true"]}
             :lint {:dependencies [[clj-kondo/clj-kondo "2024.08.01"]
                                   ;; clj-kondo requires newer clojure version because
                                   ;; it needs int?. But I'd like to keep things at Clojure 1.8.0 for now.
                                   [org.clojure/clojure "1.11.1"]]}}
  :main jarbloat.core)

