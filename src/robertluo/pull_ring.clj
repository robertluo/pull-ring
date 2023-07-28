(ns robertluo.pull-ring
  "Lasagna-pull extension for ring"
  (:require
   [sg.flybot.pullable :as pull]
   [clojure.edn :as edn]
   [cognitect.transit :as transit]))

;;----
;; Ring data schemas
;; see https://github.com/ring-clojure/ring/blob/master/SPEC

(def ring-request-schema
  "Data schema for Ring requests"
  [:map
   [:server-port :int]
   [:server-name :string]
   [:remote-addr :string]
   [:uri [:and :string [:fn #(.startsWith % "/")]]]
   [:query-string {:optional true} :string]
   [:scheme [:enum :http :https]]
   [:request-method :keyword]
   [:protocol :string]
   [:ssl-client-cert {:optional true} [:fn #(instance? java.security.cert.X509Certificate %)]]
   [:headers [:map-of :string :string]]
   [:body {:optional true} [:fn #(instance? java.io.InputStream %)]]])

^:rct/test
(comment
  (require '[malli.core :as m])
  (m/validate ring-request-schema {}) ;=> false
  (m/validate ring-request-schema {:server-port 8080 :server-name "localhost" :remote-addr "localhost"
                                   :uri "/hello" :scheme :https :request-method :get
                                   :protocol "HTTP/1.1" :headers {}}) ;=> true
  )

(def ring-response-schema
  "data schema for Ring responses"
  [:map
   [:status :int]
   [:headers [:map-of :string :string]]
   [:body {:optional true} [:fn #(instance? java.io.InputStream %)]]])

;------
; content type negotiation

(defn- read-stream
  "read an inputstream `stm`, returns clojure data"
  [stm content-type]
  (letfn [(transit-read [t] (transit/read (transit/reader stm t)))]
    (case content-type
      "application/edn"                  (edn/read (java.io.PushbackReader. (java.io.InputStreamReader. stm)))
      "application/transit+json"         (transit-read :json)
      "application/transit+json_verbose" (transit-read :json-verbose)
      "application/transit+msgpack"      (transit-read :msgpack) 
      (ex-info "Unexpected content type" {:code :unknown-value
                                          :type content-type}))))

(defn s->stream [s]
  (-> s (.getBytes "UTF-8") (java.io.ByteArrayInputStream.)))

(def o->stream (comp s->stream pr-str))

^:rct/test
(comment
  (read-stream (s->stream "{\"a\": 3}") "application/transit+json") ;=> {"a" 3}
  )

(defn- write-stream
  [content content-type]
  (letfn [(transit-write [t]
           (let [out (java.io.ByteArrayOutputStream. 4096)]
             (transit/write (transit/writer out t) content)
             out))]
    (case content-type
      "application/edn"                  (pr-str content)
      "application/transit+json"         (transit-write :json)
      "application/transit+json_verbose" (transit-write :json-verbose)
      "application/transit+msgpack"      (transit-write :msgpack)
      (ex-info "Unexpected accept type" {:code :unknown-value
                                         :type content-type}))))

^:rct/test
(comment
  (write-stream {:a 3} "application/edn") ;=> "{:a 3}"
  (write-stream {:a 3} "application/transit+json") ;=>> #(instance? java.io.OutputStream %)
  )


(defn fn-choose
  "try apply functions in `fns` return the first truethy return value"
  [& fns]
  (fn [& args]
    (some #(when-let [rtn (apply % args)] rtn) fns)))

(comment
  ((fn-choose odd? even?) 3))

(defn uri-merge
  [& strs]
  ;;TODO remove heading, trailing /
  (->> strs
       (interpose "/")
       (apply str)))

(defn model-handler
  "returns a ring handler for data `model` and its `schema` on uri `prefix`"
  [model schema prefix]
  (let [schema-handler
        (pull/qfn
         {:request-method :get :uri (uri-merge prefix "schema")}
         {:status 200
          :headers {"content-type" "application/edn"}
          :body (pr-str schema)})
        model-handler
        (pull/qfn
         {:request-method :post :uri (uri-merge prefix "query") :body '?body
          :headers {"content-type" '?content-type "accept" '?accept-type}}
         ;;TODO exception handle
         (pull/with-data-schema schema
           (let [rslt ((pull/query (read-stream ?body ?content-type)) model)]
             {:status 200
              :headers {"content-type" ?accept-type}
              :body (write-stream rslt ?accept-type)})))
        default-handler
        (constantly {:status 404})]
    (fn-choose model-handler schema-handler default-handler)))

^:rct/test
(comment
  (def ma (model-handler {:a 1 :b 20} [:map [:a :int] [:b :string]] "/pull-api"))
  (ma {:request-method :get :uri "/pull-api/schema"}) ;=>> {:status 200}

  (ma {:request-method :post :uri "/pull-api/query" 
       :headers {"content-type" "application/edn" "accept" "application/edn"}
       :body (o->stream '{:a ?a})}) ;=>> {:status 200 :body "{?a 1, &? {:a 1}}"}
  (ma {:request-method :post :uri "/none"}) ;=>{:status 404}
  )