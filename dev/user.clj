(ns user
  (:require [nextjournal.clerk :as clerk]))

(clerk/serve! {:watch-paths ["src" "notebook"]})