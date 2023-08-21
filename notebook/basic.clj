;; # Basic usage of pull-ring
(ns basic
  "Show basic usage of pull-ring"
  (:require [robertluo.pull-ring :as pr]
            [ring.middleware.stacktrace :as stacktrace]
            [ring.adapter.jetty :as jetty]))

;; ## Prepare your model
;; You have a model which you want to serve to the world:

(def myself
  {:name {:first "Robert" :last "Luo"}
   :pet {:kind :dog :breed "golden-retriver" :age 5}})

;; A good practice to spec this model with the following malli
;; spec:

(def model-spec
  [:map
   [:name [:map
           [:first :string] [:last :string]]]
   [:pet [:map
          [:kind :keyword] [:breed {:optional true} :string] [:age :int]]]])

;; You have a model maker function, a.k.a a function to return this model according
;; to a ring request.

(def my-model-maker (constantly myself))

;; ## Share it

(def my-app 
  (-> (pr/model-handler model-spec my-model-maker "/api")
      (stacktrace/wrap-stacktrace)))

(def server (jetty/run-jetty my-app {:port 8080 :join? false}))

;; ## You can pull from the endpoint remotely

(pr/remote-pull "http://localhost:8080/api/query" '{:name {:first ?}})

;; ## Shutdown the server

(.close server)
