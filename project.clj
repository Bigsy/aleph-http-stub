(defproject org.clojars.bigsy/aleph-http-stub "0.0.1"
  :repositories {"clojars" {:url "https://repo.clojars.org/"}}
  :deploy-repositories {"clojars" {:url "https://repo.clojars.org/"
                                  :sign-releases false
                                  :username :env/clojars_username
                                  :password :env/clojars_password}}
  :description "Helper for faking aleph http requests in testing"
  :url "https://github.com/Bigsy/aleph-http-stub"
  :license {:name "MIT License"
            :url "http://www.opensource.org/licenses/mit-license.php"
            :distribution :repo}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.12.0"]
                 [aleph "0.8.2"]
                 [manifold "0.4.3"]
                 [org.clj-commons/byte-streams "0.3.4"]
                 [ring/ring-codec "1.2.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.12.0"]]}})
