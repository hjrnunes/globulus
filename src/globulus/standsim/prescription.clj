(ns globulus.standsim.prescription
  (:require [charred.api :as csv]
            [clojure.java.io :as io]))

(defn generate [{:keys [file
                        prescriptions]}]
  (let [header ["IdPrescr"                                  ;; identifier of the prescription
                "NrCiclos"                                  ;; identifier of the cycle within the presciption (from 1 up to the number of cycles need to cover the planning horizon)
                "sp1"                                       ;; dominant tree species: Ec= eucalyptus; Pb= maritime pine; Pm= sone pine; Sb= cork oak
                "sp2"                                       ;; dominated tree species: Ec= eucalyptus; Pb= maritime pine; Pm= sone pine; Sb= cork oak
                "sp3"                                       ;; terciary tree species: Ec= eucalyptus; Pb= maritime pine; Pm= sone pine; Sb= cork oak
                "FMA"                                       ;; identifier of the FMA (4= even-aged; 3 uneven-aged)
                "Opt"                                       ;; identifier of the FMA option (from 1 up to the number of FMAs of the FMA type imported)
                "NyFMA"                                     ;; number of years during which you want to manage according to the combination of (FMA,opt) defined for the current cycle
                "tlag"                                      ;; number of years to wait before the prescription/FMA,opt for the first cycle will start being implemented (not active at the moment)
                "Npl"                                       ;; number of trees at planting (ha-1) (only active under MODE=1)
                "rot"                                       ;; rotation: 1=planted; 2= 1st coppice; 3=2nd coppice, ...
                "tcut"                                      ;; harvest age
                "nsprouts"                                  ;; number of sprouts left per stool (only active under MODE=1)
                "t_nsprouts"                                ;; stand age at which the sprouts selection is carrid out in the field (only active under MODE=1)
                "thin_type"                                 ;; Thinning type (1=FW; 2= residual basal area) (only active under MODE=1)
                "thin_crit"                                 ;; Thinning criteria (1=from below; 2= from above) (only active under MODE=1)
                "thin_weight"                               ;; Thinning weight (1=from below; 2= from above) (only active under MODE=1)
                "t_1st_thin"                                ;; Age of the first thinning (only active under MODE=1)
                "thin_gap"                                  ;; Thinning periodicity (only active under MODE=1)
                "t_last_thin"]                              ;; Age of the last thinning (only active under MODE=1)
        rows (->> prescriptions
                  (mapv (fn [{:keys [IdPrescr
                                     NrCiclos
                                     sp1
                                     sp2
                                     sp3
                                     FMA
                                     Opt
                                     NyFMA
                                     tlag
                                     Npl
                                     rot
                                     tcut
                                     nsprouts
                                     t_nsprouts
                                     thin_type
                                     thin_crit
                                     thin_weight
                                     t_1st_thin
                                     thin_gap
                                     t_last_thin]
                              :or   {
                                     ;IdPrescr
                                     ;NrCiclos
                                     sp1         "Ec"
                                     sp2         "Ec"
                                     sp3         "Ec"
                                     ;FMA
                                     ;Opt
                                     ;NyFMA
                                     tlag        0
                                     Npl         0
                                     ;rot
                                     ;tcut
                                     nsprouts    0
                                     t_nsprouts  0
                                     thin_type   0
                                     thin_crit   0
                                     thin_weight 0
                                     t_1st_thin  0
                                     thin_gap    1
                                     t_last_thin 0}}]
                          [IdPrescr
                           NrCiclos
                           sp1
                           sp2
                           sp3
                           FMA
                           Opt
                           NyFMA
                           tlag
                           Npl
                           rot
                           tcut
                           nsprouts
                           t_nsprouts
                           thin_type
                           thin_crit
                           thin_weight
                           t_1st_thin
                           thin_gap
                           t_last_thin]
                          )))
        csvdata (concat [header]
                        rows)]
    (if file
      (let [writer (io/writer file :encoding "windows-1252")]
        (csv/write-csv writer
                       csvdata
                       :newline :cr+lf))
      csvdata)))

(comment
  (generate {:file          "/Users/hjrnunes/workspace/hjrnunes/globulus/resources/simdata/gen_presc.csv"
             :prescriptions [{:IdPrescr "ID"
                              :NrCiclos 3
                              :FMA      4
                              :Opt      1
                              :NyFMA    10
                              :rot      1
                              :tcut     10}
                             {:IdPrescr "ID"
                              :NrCiclos 3
                              :FMA      4
                              :Opt      1
                              :NyFMA    10
                              :rot      2
                              :tcut     10}
                             {:IdPrescr "ID"
                              :NrCiclos 3
                              :FMA      4
                              :Opt      1
                              :NyFMA    10
                              :rot      3
                              :tcut     10}]})

  )
