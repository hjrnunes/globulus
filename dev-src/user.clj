(ns user
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk.viewer :as clerk-viewer]
            [nextjournal.clerk.tap]
            [org.scicloj.casagemas]
            [org.scicloj.casagemas.dataset]
            [clojure.string]))

(clerk-viewer/reset-viewers!
  ;; :default
  (find-ns 'nextjournal.clerk.tap)
  (-> clerk-viewer/default-viewers
      (clerk-viewer/add-viewers [nextjournal.clerk.tap/tap-viewer])
      (clerk-viewer/add-viewers
        (concat (vals org.scicloj.casagemas/viewer-descriptions)
                [org.scicloj.casagemas.dataset/viewer-description]
                ;(vals org.scicloj.casagemas.kroki/viewer-descriptions)
                ))))

; or let Clerk watch the given `:paths` for changes
(clerk/serve! {:watch-paths    ["src"]
               :show-filter-fn (fn [path]
                                 (println path)
                                 (clojure.string/starts-with? path "src/globulus/notebooks"))})


(clerk/show! "src/globulus/notebooks/report.md")

;(do
;  (def user/portal ((requiring-resolve 'portal.api/open) {:launcher :intellij}))
;  (add-tap (requiring-resolve 'portal.api/submit)))