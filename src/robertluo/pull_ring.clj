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

(defn- read-stream
  "read an inputstream `stm`, returns clojure data"
  [stm]
  (edn/read (java.io.PushbackReader. (java.io.InputStreamReader. stm))))

(defn fn-choose
  "try apply functions in `fns` return the first truethy return value"
  [& fns]
  (fn [& args]
    (some #(when-let [rtn (apply % args)] rtn) fns)))

(comment
  ((fn-choose odd? even?) 3)
  )

(defn model-handler
  "returns a ring handler for data `model` and its `schema`"
  [model schema]
  (let [schema-handler 
        (pull/qfn
         '{:request-method :get :uri "/pull-api/schema"}
         {:status 200 
          :headers {"content-type" "application/edn"}
          :body (pr-str schema)})
        model-handler
        (pull/qfn
         '{:request-method :post :uri "/pull-api/query" :body ?body}
         ;;TODO exception handle
         (pull/with-data-schema schema
           {:status 200
            :headers {"content-type" "application/edn"}
            :body (pr-str ((pull/query (read-stream ?body)) model))}))
        default-handler
        (constantly {:status 404})]
    (fn-choose model-handler schema-handler default-handler)))

^:rct/test
(comment
  (def ma (model-handler {:a 1 :b 20} [:map [:a :int] [:b :string]]))
  (ma {:request-method :get :uri "/pull-api/schema"}) ;==> {:status 200}
  
  (defn o->stream [o]
    (-> o (pr-str) (.getBytes "UTF-8") (java.io.ByteArrayInputStream.)))
  (ma {:request-method :post :uri "/pull-api/query" :body (o->stream '{:a ?a})}) ;==> {:status 200 :body ""}
  (ma {:request-method :post :uri "/none"}) ;=>{:status 404}
  )