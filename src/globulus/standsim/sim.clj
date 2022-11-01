(ns globulus.standsim.sim
  (:require [charred.api :as csv]
            [clojure.java.io :as io]
            [babashka.fs :as fs]
            [globulus.standsim.climate :as climate]
            [globulus.standsim.prescription :as prescription]
            [globulus.standsim.stand :as stand]
            [globulus.standsim.operations :as ops]
            [globulus.standsim.consumables :as consumables]
            [globulus.standsim.assortments :as assortments]
            [globulus.standsim.fma :as fma]
            [clojure.math.combinatorics :refer [cartesian-product]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.pprint :as pprint])
  (:import (java.nio.file FileAlreadyExistsException)))


(defn generate-config [{:keys [file
                               mode                         ;; Simulation mode, indicates whether to follow the instructions in the FMA file (0) or in the prescription file (1) (0-seguir FMA só usa tcorte da prescrição, 1-seguir prescrição)
                               plan                         ;; Number of years for which you want to run the simulation
                               model-type                   ;; Model type: empirial stand level model (1), empirical individual tree level model (2), process-based stand level model (3)
                               stand                        ;; only filled in if ModelType=2 (path to the stand inventory file)
                               climate-anual                ;; only filled in if ModelType<>3 (path to the climate file)
                               operations                   ;; path to the cost of operations file
                               consumables                  ;; path to the cost of consumables file
                               assortments                  ;; path to the asortments and wood prices file
                               prescriptions                ;; path to the prescriptions file
                               n-fma3                       ;; number of uneven-aged FMA files to be used in the simulation
                               n-fma4                       ;; number of even-aged FMA files to be used in the simulation
                               fmas
                               yield-table                  ;; Whether you wish the output_yieldtable.csv file (keeps track of the evolution of all stand variables) to be written: yes (1), no (0)
                               pl                           ;; Whether you wish the output_PL.csv file (input for the Linear Programming) to be written: yes (1), no (0)
                               tree]                        ;; Whether you wish the output_tree.csv file (keeps track of the growth of each tree) to be written: yes (1), no (0)
                        :or   {mode        0
                               plan        10
                               model-type  1
                               n-fma3      0
                               yield-table 1
                               pl          0
                               tree        0}}]
  (let [csvdata (concat
                  [["MODE:" mode]
                   ["Plan_horiz" plan]
                   ["ModelTYPE:" model-type]
                   ["3PG_parameters:" "NNN"]
                   ["Inventory_pov:" stand]
                   ["Inventory_arv1:" "NNN"]
                   ["Inventory_arv2:" "NNN"]
                   ["cod_3PG:" 0]
                   ["cod_solo:" 0]
                   ["cod_clima:" 0]
                   ["Normais_anuais:" climate-anual]
                   ["Normais_mensais:" "NNN"]
                   ["Series_temporais:" "NNN"]
                   ["Economic_name:" operations]
                   ["Consumables_name:" consumables]
                   ["Assortments:" assortments]
                   ["Prescriptions:" prescriptions]
                   ["Number_FMA3:" n-fma3]
                   ["Number_FMA4:" (or n-fma4 (count fmas))]]
                  (->> fmas (map (fn [fma] [fma ""])))
                  [["YieldTable:" yield-table]
                   ["Output_PL:" pl]
                   ["Output_Tree:" tree]])]
    (if file
      (let [writer (io/writer file :encoding "windows-1252")]
        (csv/write-csv writer
                       csvdata
                       :newline :cr+lf))
      csvdata)))

(comment
  (generate-config {:file          "/Users/hjrnunes/workspace/hjrnunes/globulus/resources/simdata/gen_cfg.csv"
                    :mode          0
                    :plan          30
                    :model-type    1
                    :stand         "input_stand.csv"
                    :climate-anual "input_clima.csv"
                    :operations    "Operations.csv"
                    :consumables   "Consumables.csv"
                    :assortments   "Assortments.csv"
                    :prescriptions "input_prescr.csv"
                    :yield-table   1
                    :pl            0
                    :tree          0
                    :fmas          ["FMA41_Ec_Regular.csv"]}))


(def sites {:coruche {:altitude   14
                      :site-index 15}
            :alhadas {:altitude   105
                      :site-index 20}})

(def op-types (-> (io/resource "simdata/op_types.edn")
                  (str)
                  (slurp)
                  (edn/read-string)))

(defn path [run-dir p]
  (str (fs/path run-dir p)))

(defn relative [run-dir p]
  (str (fs/relativize run-dir p)))

(defn next-dir [{:keys [run-directory site rate density pulpwood-price]}]
  (let [candidate (format "%s_%s_%s_%s" (name site) rate density pulpwood-price)
        cwd (fs/path run-directory)
        existing (some->> (fs/list-dir cwd)
                          (map #(fs/relativize cwd %))
                          (filter #(str/starts-with? % candidate))
                          (map (comp last #(str/split % #"_") str))
                          (map parse-long))
        counter (if (seq existing) (apply max existing) 0)]
    (fs/path run-directory (format "%s_%s_%s_%s_%s" (name site) rate density pulpwood-price (inc counter)))))

(defn generate-run [{:keys [sim-mode
                            plan-years
                            model-type
                            site
                            rate
                            additional-rates
                            pulpwood-price
                            density
                            maintenance-costs
                            prescriptions
                            fma-data
                            :run-directory]
                     :as   sim-params}]
  (let [run-dir (next-dir sim-params)
        path (comp str (partial fs/path run-dir))
        relativize (comp str (partial fs/relativize run-dir))

        {:keys [altitude site-index]} (get sites site)
        fma-files (->> fma-data
                       (sort-by :type)
                       (map (fn [{:keys [id type]}]
                              (path (format "fma_%s_%s.csv" id type)))))
        prescriptions-file (path "prescriptions.csv")
        assortments-file (path "assortments.csv")
        consumables-file (path "consumables.csv")
        ops-file (path "operations.csv")
        climate-file (path "input_clima.csv")
        stands-file (path "input_stand.csv")
        {:keys [id_met] :as clima-site} (get climate/sites site)]
    (try (fs/create-dir run-dir) (catch FileAlreadyExistsException _))
    (try (fs/create-dir (fs/path run-dir "output")) (catch FileAlreadyExistsException _))
    (doseq [fma-file fma-files
            data (->> fma-data
                      (sort-by :type))]
      (fma/generate {:file     fma-file
                     :data     data
                     :density  density
                     :op-types op-types}))
    (prescription/generate {:file          prescriptions-file
                            :prescriptions prescriptions})
    (assortments/generate {:file           assortments-file
                           :pulpwood-price pulpwood-price})
    (consumables/generate {:file consumables-file})
    (ops/generate {:file              ops-file
                   :rate              rate
                   :additional-rates  additional-rates
                   :maintenance-costs maintenance-costs})
    (climate/generate (merge {:file climate-file}
                             clima-site))
    (stand/generate {:file     stands-file
                     :idstand  1
                     :id_presc "ID"
                     :id_meteo id_met
                     :Altitude altitude
                     :year     2022
                     :S        site-index})
    (generate-config {:file          (path "ini_standsSIM.csv")
                      :mode          sim-mode
                      :plan          plan-years
                      :model-type    model-type
                      :stand         (relativize stands-file)
                      :climate-anual (relativize climate-file)
                      :operations    (relativize ops-file)
                      :consumables   (relativize consumables-file)
                      :assortments   (relativize assortments-file)
                      :prescriptions (relativize prescriptions-file)
                      :yield-table   1
                      :pl            0
                      :tree          0
                      :fmas          (->> fma-files
                                          (mapv relativize))})
    (spit (path "params.edn") (with-out-str (pprint/pprint sim-params)))
    ))

(defn generate-runs [{:keys [rates
                             densities
                             coppice-factors
                             pulpwood-prices]}]
  (let [args (cartesian-product rates
                                densities
                                coppice-factors
                                pulpwood-prices)]
    (doseq [[rate density coppice-factor pulpwood-price] args]
      (generate-run {:plan-years        20
                     :site              :alhadas
                     :rate              rate
                     :additional-rates  [3 4 5 6]
                     :maintenance-costs 30
                     :pulpwood-price    pulpwood-price
                     :density           density
                     :coppice-factor    coppice-factor
                     :prescriptions     [{:IdPrescr "ID"
                                          :NrCiclos 2
                                          :FMA      4
                                          :Opt      1
                                          :NyFMA    10
                                          :rot      1
                                          :tcut     10}
                                         {:IdPrescr "ID"
                                          :NrCiclos 2
                                          :FMA      4
                                          :Opt      1
                                          :NyFMA    10
                                          :rot      2
                                          :tcut     10}]

                     :fma-data          [{:id    4,         ;; Forest Management Approach ID (4 for even-aged stands; 3 for uneven-aged stands)
                                          :type  2,         ;; Indicator of management (1 represents plantation management, 2 represents plantation followed by coppice management after the first harvest)
                                          :years 20,        ;; Maximum number of years for which the management operations will be defined (when the indicator of management is set to 2, the number of years defined will be applied both to the plantation and the coppice)
                                          :ops   [{:t 1, :r 0, :op "Npl", :desc "Número de árvores plantadas ha-1" :v density}
                                                  {:t 1, :r 0, :op "BeatUp", :desc "Percentagem de árvores mortas e substituídas" :v 15}
                                                  {:t 1, :r 0, :op "1_SachaAmontoa", :desc "Sacha e amontoa" :v 1}
                                                  {:t 1, :r 0, :op "1_PlantRdFdCont", :desc "Plantação de resinosas e folhosas em contentor" :v 1}
                                                  {:t 1, :r 0, :op "1_RetanchaRdFdCont", :desc "Retancha de resinosas e folhosas em contentor" :v 1}
                                                  {:t 1, :r 0, :op "1_Adubacao", :desc "Aplicação manual de adubo" :v 1}
                                                  {:t 1, :r 0, :op "3_GradagemVegEspPoucoDesenv", :desc "Gradagem de vegetação espontânea pouco desenvolvida" :v 1}
                                                  {:t 1, :r 0, :op "3_Ripagem3dentes", :desc "Ripagem a 3 m de profundidade com 3 dentes" :v 1}
                                                  {:t 2, :r 0, :op "3_LimpezaMatoGradeDiscos", :desc "Limpeza de mato com grade de discos" :v 1}
                                                  {:t 2, :r 0, :op "3_AplicAduboLinhaRodas", :desc "Aplicação de adubo em linha, em profundidade - rodas" :v 1}
                                                  {:t 4, :r 0, :op "3_LimpezaMatoGradeDiscos", :desc "Limpeza de mato com grade de discos" :v 1}
                                                  {:t 4, :r 0, :op "3_AplicAduboLinhaRodas", :desc "Aplicação de adubo em linha, em profundidade - rodas" :v 1}

                                                  {:t 3, :r 1, :op "ShootSel", :desc "Média do número de varas deixadas por cepo após selecção" :v coppice-factor}
                                                  {:t 3, :r 1, :op "2_RedDensEc", :desc "Selecção de varas de eucalipto" :v 1}
                                                  {:t 3, :r 1, :op "3_AplicAduboLinhaRodas", :desc "Aplicação de adubo em linha, em profundidade - rodas" :v 1}
                                                  {:t 3, :r 1, :op "3_LimpezaMatoGradeDiscos", :desc "Limpeza de mato com grade de discos" :v 1}
                                                  {:t 4, :r 1, :op "3_AplicAduboLinhaRodas", :desc "Aplicação de adubo em linha, em profundidade - rodas" :v 1}
                                                  {:t 5, :r 1, :op "3_LimpezaMatoGradeDiscos", :desc "Limpeza de mato com grade de discos" :v 1}
                                                  ]}]

                     :model-type        1
                     :sim-mode          0
                     :run-directory     "/Users/hjrnunes/workspace/hjrnunes/globulus/tmp/runs"
                     }))))

(comment
  (generate-runs {:rates           [3 4 5 6]
                  :pulpwood-prices [28 29 30 31]
                  :densities       [1000 1250 1450 1600]
                  :coppice-factors [1.0, 1.5, 2.3, 3.0]})
  )

(comment
  (generate-runs {:rates           [3]
                  :pulpwood-prices [28]
                  :densities       [1000]
                  :coppice-factors [1.0]})
  )

(comment
  (generate-run {:plan-years        20
                 :site              :alhadas
                 :rate              6
                 :additional-rates  [3 4 5 6]
                 :maintenance-costs 5
                 :pulpwood-price    29
                 :density           1250
                 :coppice-factor    1.5
                 :prescriptions     [{:IdPrescr "ID"
                                      :NrCiclos 2
                                      :FMA      4
                                      :Opt      1
                                      :NyFMA    10
                                      :rot      1
                                      :tcut     10}
                                     {:IdPrescr "ID"
                                      :NrCiclos 2
                                      :FMA      4
                                      :Opt      1
                                      :NyFMA    10
                                      :rot      2
                                      :tcut     10}]

                 :fma-fn            [{:id    4,             ;; Forest Management Approach ID (4 for even-aged stands; 3 for uneven-aged stands)
                                      :type  2,             ;; Indicator of management (1 represents plantation management, 2 represents plantation followed by coppice management after the first harvest)
                                      :years 20,            ;; Maximum number of years for which the management operations will be defined (when the indicator of management is set to 2, the number of years defined will be applied both to the plantation and the coppice)
                                      :ops   [{:t 1, :r 0, :op "Npl", :desc "Número de árvores plantadas ha-1" :v 1250}
                                              {:t 1, :r 0, :op "BeatUp", :desc "Percentagem de árvores mortas e substituídas" :v 15}
                                              {:t 1, :r 0, :op "1_SachaAmontoa", :desc "Sacha e amontoa" :v 1}
                                              {:t 1, :r 0, :op "1_PlantRdFdCont", :desc "Plantação de resinosas e folhosas em contentor" :v 1}
                                              {:t 1, :r 0, :op "1_RetanchaRdFdCont", :desc "Retancha de resinosas e folhosas em contentor" :v 1}
                                              {:t 1, :r 0, :op "1_Adubacao", :desc "Aplicação manual de adubo" :v 1}
                                              {:t 1, :r 0, :op "3_GradagemVegEspPoucoDesenv", :desc "Gradagem de vegetação espontânea pouco desenvolvida" :v 1}
                                              {:t 1, :r 0, :op "3_Ripagem3dentes", :desc "Ripagem a 3 m de profundidade com 3 dentes" :v 1}
                                              {:t 2, :r 0, :op "3_LimpezaMatoGradeDiscos", :desc "Limpeza de mato com grade de discos" :v 1}
                                              {:t 2, :r 0, :op "3_AplicAduboLinhaRodas", :desc "Aplicação de adubo em linha, em profundidade - rodas" :v 1}
                                              {:t 4, :r 0, :op "3_LimpezaMatoGradeDiscos", :desc "Limpeza de mato com grade de discos" :v 1}
                                              {:t 4, :r 0, :op "3_AplicAduboLinhaRodas", :desc "Aplicação de adubo em linha, em profundidade - rodas" :v 1}

                                              {:t 3, :r 1, :op "ShootSel", :desc "Média do número de varas deixadas por cepo após selecção" :v 1.5}
                                              {:t 3, :r 1, :op "2_RedDensEc", :desc "Selecção de varas de eucalipto" :v 1}
                                              {:t 3, :r 1, :op "3_AplicAduboLinhaRodas", :desc "Aplicação de adubo em linha, em profundidade - rodas" :v 1}
                                              {:t 3, :r 1, :op "3_LimpezaMatoGradeDiscos", :desc "Limpeza de mato com grade de discos" :v 1}
                                              {:t 4, :r 1, :op "3_AplicAduboLinhaRodas", :desc "Aplicação de adubo em linha, em profundidade - rodas" :v 1}
                                              {:t 5, :r 1, :op "3_LimpezaMatoGradeDiscos", :desc "Limpeza de mato com grade de discos" :v 1}
                                              ]}]

                 :model-type        1
                 :sim-mode          0
                 :run-directory     "/Users/hjrnunes/workspace/hjrnunes/globulus/tmp/runs"
                 })
  )
