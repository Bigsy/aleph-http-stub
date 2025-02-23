(ns aleph-http.stub
  (:require [aleph.http :as http]
            [manifold.deferred :as d]
            [clojure.string :as str]))

(def ^:dynamic *stub-routes* {})
(def ^:dynamic *in-isolation* false)

(defn- matches-route? [route request-url]
  (cond
    (string? route) (= route request-url)
    (instance? java.util.regex.Pattern route) (re-find route request-url)
    (fn? route) (route request-url)
    :else false))

(defn- find-stub [request]
  (let [request-url (str (:url request))]
    (->> *stub-routes*
         (filter (fn [[route _]]
                  (matches-route? route request-url)))
         first
         second)))

(defn- create-response [response-fn request]
  (let [response (if (fn? response-fn)
                   (response-fn request)
                   response-fn)]
    (d/success-deferred response)))

(defn stub-request
  "Internal function used by with-http-stub macro"
  [original-fn request]
  (if-let [stub-fn (find-stub request)]
    (create-response stub-fn request)
    (if *in-isolation*
      (throw (ex-info "No matching stub found and running in isolation mode" 
                     {:url (:url request)}))
      (original-fn request))))

(defmacro with-http-stub
  "Takes a map of route/response-fn pairs and executes the body with HTTP requests stubbed.
   Routes can be:
   - Exact URL strings
   - Regular expressions
   - Functions that take a URL and return true/false
   
   Response functions should take a request map and return a response map with:
   {:status 200 :headers {} :body \"response\"}"
  [routes & body]
  `(binding [*stub-routes* ~routes]
     (with-redefs [aleph.http/request (fn [request#] 
                                      (stub-request http/request request#))]
       ~@body)))

(defmacro with-http-stub-in-isolation
  "Like with-http-stub, but throws an exception if no matching stub is found"
  [routes & body]
  `(binding [*in-isolation* true]
     (with-http-stub ~routes ~@body)))
