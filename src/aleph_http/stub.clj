(ns aleph-http.stub
  (:require [aleph.http :as http]
            [manifold.deferred :as d]
            [clojure.string :as str]
            [ring.util.codec :as ring-codec]
            [clojure.test :refer [is]]))

(def ^:dynamic *stub-routes* {})
(def ^:dynamic *in-isolation* false)
(def ^:dynamic *call-counts* (atom {}))
(def ^:dynamic *expected-counts* (atom {}))

(defn- normalize-query-params [params]
  (when params
    (into {} (for [[k v] params]
               [(keyword k) (str v)]))))

(defn- parse-query-string [query-string]
  (if (str/blank? query-string)
    {}
    (normalize-query-params (ring-codec/form-decode query-string))))

(defn- get-request-query-params [request]
  (let [url (str (:url request))
        [_ query-string] (str/split url #"\?" 2)]
    (or (some-> request :query-params normalize-query-params)
        (some-> query-string parse-query-string)
        {})))

(defn- normalize-request [request]
  (let [query-params (get-request-query-params request)
        [url _] (str/split (str (:url request)) #"\?" 2)]
    (-> request
        (assoc :query-params query-params)
        (assoc :url url))))

(defn- matches-route? [route request]
  (let [request-url (:url request)
        [request-base-url _] (str/split request-url #"\?" 2)]
    (cond
      (string? route) (let [[route-url _] (str/split route #"\?" 2)]
                        (= route-url request-base-url))
      (instance? java.util.regex.Pattern route) (boolean (re-find route request-base-url))
      (fn? route) (route request-base-url)
      :else false)))

(defn- find-matching-handler [request]
  (let [request-method (or (:request-method request) :get)]
    (some (fn [[route handlers]]
            (when (matches-route? route request)
              (let [handler (or (get handlers request-method)
                               (get handlers :any))
                    times-map (:times handlers)
                    times (if (map? times-map)
                           (get times-map request-method)  ; Get method-specific times
                           times-map)                     ; Or global times
                    route-key (str route ":" (name request-method))]
                ;; Set up expected counts if :times is specified
                (when times
                  (swap! *expected-counts* assoc route-key times))
                ;; Update call count
                (when handler
                  (swap! *call-counts* update route-key (fnil inc 0)))
                handler)))
          *stub-routes*)))

(defn- create-response [handler request]
  (let [response (if (fn? handler)
                   (handler request)
                   handler)]
    (if (d/deferred? response)
      response
      (d/success-deferred response))))

(defn stub-request
  "Internal function used by with-http-stub macro"
  [original-fn request]
  (let [normalized-request (normalize-request request)
        request-url (str (:url normalized-request))
        request-method (or (:request-method normalized-request) :get)
        _ (println "DEBUG: request-url:" request-url)
        _ (println "DEBUG: query-params:" (:query-params normalized-request))
        handler (find-matching-handler normalized-request)]
    (if handler
      (let [response (create-response handler normalized-request)]
        (println "DEBUG: response:" response)
        response)
      (if *in-isolation*
        (throw (ex-info "No matching stub found and running in isolation mode" 
                       {:url request-url
                        :method request-method}))
        (original-fn request)))))

(defn verify-call-counts! []
  (doseq [[route-key expected-count] @*expected-counts*]
    (let [actual-count (get @*call-counts* route-key 0)]
      (when (not= expected-count actual-count)
        (throw (Exception.
                (format "Expected route '%s' to be called %d times but was called %d times"
                        route-key expected-count actual-count)))))))

(defmacro with-http-stub
  "Takes a map of route/response-fn pairs and executes the body with HTTP requests stubbed.
   Routes can be:
   - Exact URL strings
   - Regular expressions
   - Functions that take a URL and return true/false
   
   Response functions should take a request map and return a response map with:
   {:status 200 :headers {} :body \"response\"}"
  [routes & body]
  `(binding [*stub-routes* ~routes
             *call-counts* (atom {})
             *expected-counts* (atom {})]
     (with-redefs [aleph.http/request (fn [request#] 
                                      (stub-request http/request request#))]
       (let [result# (do ~@body)]
         (verify-call-counts!)
         result#))))

(defmacro with-http-stub-in-isolation
  "Like with-http-stub, but throws an exception if no matching stub is found"
  [routes & body]
  `(binding [*in-isolation* true]
     (with-http-stub ~routes ~@body)))
