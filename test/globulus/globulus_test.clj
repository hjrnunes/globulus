(ns globulus.globulus-test
  (:require [clojure.test :refer :all]
            [globulus.globulus :as glob]
            [clojure.java.io :as io]
            [tablecloth.api :as tc]
            [tech.v3.dataset :as ds]))


(deftest npv
  (let [dataset (-> (io/resource "simulacao_eucalipto.csv")
                    (str)
                    (tc/dataset {:n-initial-skip-rows 8}))
        dataset (-> dataset
                    (tc/drop-rows (->> (tc/row-count dataset)
                                       (iterate dec)
                                       (take 6)))
                    (ds/column-cast "Ano" :int16)
                    (tc/rename-columns {"Ano"                     :year
                                        "Custo (Val. corr.)"      :cost
                                        "Rendimento (Val. corr.)" :revenue})
                    (glob/net-present-value 0.035))]
    (is (= 48.02411445570419
           (first (:present-value (tc/last dataset)))))))



(deftest lev
  (let [data (concat [{:year 18 :revenue 96 :cost 0}
                      {:year 25 :revenue 160 :cost 0}
                      {:year 30 :revenue 912 :cost 0}
                      {:year 0 :revenue 0 :cost 40}
                      {:year 0 :revenue 0 :cost 40}]
                     (->> (range 1 31)
                          (map (fn [y] {:year y :revenue 0 :cost 1.5}))))
        dataset (-> (tc/dataset data)
                    (tc/order-by :year)
                    (glob/land-expectation-value 30 0.04))]
    (is (= 916.7643499382615
           (first (:land-expectation-value (tc/last dataset)))))))
