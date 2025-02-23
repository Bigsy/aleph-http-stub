(defproject org.clojars.bigsy/aleph-http-stub "0.0.1"
  :description "Helper for faking aleph http requests in testing"
  :url "https://github.com/yourusername/aleph-http-stub"
  :license {:name "MIT License"
            :url "http://www.opensource.org/licenses/mit-license.php"
            :distribution :repo}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [aleph "0.7.0"]
                 [manifold "0.4.1"]
                 [org.clj-commons/byte-streams "0.3.4"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.11.1"]]}})
