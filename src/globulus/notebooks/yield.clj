(ns globulus.notebooks.yield
  {:nextjournal.clerk/visibility {:code :fold}}
  (:require [nextjournal.clerk :as clerk]
            [tablecloth.api :as tc]
            [clojure.java.io :as io]
            [globulus.globulus :as g]
            [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.edn :as edn]))


^{:nextjournal.clerk/visibility {:result :hide}}
(def titles {"Ndead"      "Number of dead trees per hectare (ha-1)"
             "N_ing"      "Number of ingrowth trees per hectare (ha-1)"
             "Fw"         "Wilson factor – relative stand density measure (varies from 0.2 up to 0.4)"
             "G"          "Stand basal area (m2 ha-1)"
             "dg"         "Quadratic mean diameter at breast height – corresponds to the dimeter of the average tree in the stand(cm)"
             "Vu"         "Standing volume under-bark including stump (m3 ha-1)"
             "Vb"         "Volume of the bark including stump (m3 ha-1)"
             "Vst"        "Volume of the stump (m3 ha-1)"
             "V"          "Standing volume over-bark including stump (m3 ha-1)"
             "V_as1"      "Harvested volume of assortment 1 (m3 ha-1)"
             "V_as2"      "Harvested volume of assortment 2 (m3 ha-1)"
             "V_as3"      "Harvested volume of assortment 3 (m3 ha-1)"
             "V_as4"      "Harvested volume of assortment 4 (m3 ha-1)"
             "V_as5"      "Harvested volume of assortment 5 (m3 ha-1)"
             "Vharv"      "Total harvested volume sum of all assortments (thinning + final harvest)"
             "Vtot"       "?"
             "maiV"       "volume mean annual increment (m3 ha-1)"
             "iV"         "volume current annual increment (m3 ha-1)"
             "Ww"         "Stand wood biomass (Mg ha-1)"
             "Wl"         "Stand leaves biomass (Mg ha-1)"
             "Wb"         "Stand bark biomass (Mg ha-1)"
             "Wbr"        "Stand branches biomass (Mg ha-1)"
             "Wa"         "Stand aboveground biomass (Mg ha-1)"
             "Wr"         "Stand root biomass (Mg ha-1)"
             "WPmnuts"    "Stand pine-nuts biomass - stone pine (Mg ha-1)"
             "Wtop"       "Stand residues biomass - top biomass (Mg ha-1)"
             "Wtopbr"     "Stand residues biomass - top and branches biomass (Mg ha-1)"
             "Wb_bic"     "Stand residues biomass - bark (Mg ha-1)"
             "Wbrl"       "Stand residues biomass - leaves and branches (Mg ha-1)"
             "C_seq_prod" "Carbon sequestered in the products (Mg ha-1)"
             "PC_tot"     "Total production costs from inside and outside the forestry wood chain and labour costs (seedling, seeds) (€)"
             "PC_in"      "Production costs from inside the forestry wood chain (seedling, seeds) (€)"
             "PC_out"     "Production costs from outside the forestry wood chain (fertilizers, fences,...) (€)"
             "PC_lab"     "Production costs labour (€)"
             "R_wood"     "Wood revenues (€)"
             "R_biom"     "Biomass revenues (€)"
             "R_Pmnuts"   "Pinenuts revenues (€)"
             "NPV"        "Net present value (€)"
             "NPVsum"     "Sum of the net present value (€)"
             "EEA"        "? (€)"})

^{:nextjournal.clerk/visibility {:result :hide}}
(def runs-dir "/Users/hjrnunes/workspace/hjrnunes/globulus/tmp/runs")

^{:nextjournal.clerk/visibility {:result :hide}}
(def runs ["alhadas_3_1"
           "alhadas_4_1"
           "alhadas_5_1"
           "alhadas_6_1"])

^{:nextjournal.clerk/visibility {:result :hide}}
(defn load-run [runs-dir run]
  (let [run-dir (fs/path runs-dir run)
        params-file (fs/path run-dir "params.edn")
        yt-file (fs/path run-dir "output" "output_YieldTable.csv")]
    {:run         run
     :params      (-> params-file
                      (str)
                      (slurp)
                      (edn/read-string))
     :yield-table (-> yt-file
                      (str)
                      (tc/dataset))}))

^{:nextjournal.clerk/visibility {:result :hide}}
(def yield-table-ds (->> runs
                         (mapv (partial load-run runs-dir))
                         (mapv (fn [{:keys [params yield-table]}]
                                 (let [{:keys [rate site plan-years]} params]
                                   (-> yield-table
                                       (tc/add-column :t (concat [0] (range 0 (tc/row-count yield-table))))
                                       (tc/add-column :rate rate)
                                       (tc/add-column :site (name site))
                                       (g/land-expectation-value plan-years (/ rate 100) {:cost-col    "PC_tot"
                                                                                          :revenue-col "R_wood"
                                                                                          :t-col       :t})))))
                         (apply tc/concat)))

^{:nextjournal.clerk/visibility {:result :hide}}
;(def yield-table-ds (let [dataset (tc/dataset "tmp/runs/run-test/output/output_YieldTable.csv")]
;                      (-> dataset
;                          (assoc :t (concat [0] (range 0 (tc/row-count dataset))))
;                          (g/land-expectation-value 10 0.04 {:cost-col    "PC_tot"
;                                                             :revenue-col "R_wood"
;                                                             :t-col       :t})
;                          (tc/reorder-columns ["NPV" "NPVsum" :present-value :land-expectation-value :lv-net "PC_tot" "R_wood" "EEA"]))))

^{:nextjournal.clerk/visibility {:result :hide}}
(def yield-table (-> yield-table-ds
                     (tc/rows :as-maps)
                     (vec)))

(clerk/table yield-table)


;; ### Despesas anuais
;; - *IN cadeia de valor florestal*: sementes, pés de árvore, etc.
;; - *EX cadeira de valor florestal*: fertilizantes, vedações, etc.
(clerk/vl
  {:$schema   "https://vega.github.io/schema/vega-lite/v5.json"
   :width     400
   :height    500
   :title     "Despesa por tipo (€ / ha)"
   :data      {:values yield-table}
   :transform [{:filter "datum.rate === 3"}
               {:fold ["PC_in", "PC_out", "PC_lab"]
                :as   ["cost_type" "cost"]}]
   :mark      {:type    "bar"
               :tooltip true}
   :encoding  {:x     {:field "year"
                       :title "Ano"}
               :y     {:aggregate "sum"
                       :field     "cost"
                       :title     "Despesa"}
               :color {:field  "cost_type"
                       :legend {:title     "Tipo de despesa"
                                :labelExpr "datum.label == 'PC_in' ? 'IN cadeia de valor florestal' : datum.label == 'PC_out' ? 'EX cadeia de valor florestal' :datum.label == 'PC_lab' ? 'Mão de obra' : 'Video Game'"}}}})

;; ### Cash flow
(clerk/vl
  {:$schema   "https://vega.github.io/schema/vega-lite/v5.json"
   :width     200
   :height    200
   :title     "Cash flow"
   :data      {:values yield-table}
   :transform [{:filter "datum.rate === 3"}
               {:aggregate [{:op "sum" :field "PC_tot" :as "Despesa"}
                            {:op "sum" :field "R_wood" :as "Receita"}]}
               {:fold ["Despesa", "Receita"]
                :as   ["flow_type" "amount"]}]
   :mark      {:type    "bar"
               :tooltip true}
   :encoding  {:x     {:field "flow_type"
                       :type  "nominal"
                       :title nil
                       :axis  {:labelAngle   0
                               :labelPadding 10
                               :ticks        false}}
               :y     {:field "amount"
                       :type  "quantitative"}
               :color {:field  "flow_type"
                       :legend nil}}})

;; ### Rendibilidade

(clerk/vl
  {:$schema "https://vega.github.io/schema/vega-lite/v5.json"
   :data    {:values yield-table}
   :columns 1
   :concat  [{:title    "LEV acumulated"
              :mark     {:type "bar" :tooltip true}
              :encoding {:facet {:field "rate" :columns 3}
                         :x     {:field "year"
                                 :title "Ano"}
                         :y     {:aggregate "max"
                                 :field     :land-expectation-value
                                 :type      "quantitative"
                                 :title     nil}}}

             {:title    "LEV net per year"
              :mark     {:type "bar" :tooltip true}
              :encoding {:facet {:field "rate" :columns 3}
                         :x     {:field "year"
                                 :title "Ano"}
                         :y     {:aggregate "max"
                                 :field     :lv-net
                                 :type      "quantitative"
                                 :title     nil}}}

             {:title    "NPV acumulated"
              :mark     {:type "bar" :tooltip true}
              :encoding {:facet {:field "rate" :columns 3}
                         :x     {:field "year"
                                 :title "Ano"}
                         :y     {:aggregate "max"
                                 :field     "NPVsum"
                                 :type      "quantitative"
                                 :title     nil}}}

             {:title    "NPV net per year"
              :mark     {:type "bar" :tooltip true}
              :encoding {:facet {:field "rate" :columns 3}
                         :x     {:field "year"
                                 :title "Ano"}
                         :y     {:aggregate "max"
                                 :field     "NPV"
                                 :type      "quantitative"
                                 :title     nil}}}
             ]})

