package app.d6d.content.srd521it

import app.d6d.i18n.AppLanguage
import app.d6d.rules.character.EffectCondition
import app.d6d.rules.character.EffectTarget
import app.d6d.rules.character.RuleEffect
import app.d6d.rules.character.RuleElementDefinition
import app.d6d.rules.character.RuleElementKind

/**
 * Talenti e azioni comuni presenti nelle edizioni italiana e inglese dell'SRD 5.2.1.
 *
 * I nomi delle azioni esposti al Compendio sono all'infinito; nel testo è conservata
 * la denominazione nominale usata dal Glossario delle regole del documento ufficiale.
 */
object SrdFeatsAndActions {
    private val italianFeats: List<RuleElementDefinition> = listOf(
        RuleElementDefinition(
            id = "srd521-it:feat:origin:abile",
            name = "Abile",
            kind = RuleElementKind.ORIGIN_FEAT,
            description = """
                Il personaggio ottiene competenza in una combinazione di tre abilità o strumenti a scelta.

                Ripetibile. Questo talento è ottenibile più di una volta.
            """.trimIndent(),
            prerequisite = "Nessuno",
            sourcePage = 98,
            activation = "Passiva",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:origin:aggressore-selvaggio",
            name = "Aggressore selvaggio",
            kind = RuleElementKind.ORIGIN_FEAT,
            description = """
                Il personaggio si è allenato per sferrare colpi particolarmente letali. Una volta per turno, quando
                colpisce un bersaglio con un'arma, puoi tirare due volte per i danni dell'arma e scegliere il risultato
                che preferisci.
            """.trimIndent(),
            prerequisite = "Nessuno",
            sourcePage = 98,
            activation = "Nessuna azione; una volta per turno, quando colpisce con un'arma",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:origin:allerta",
            name = "Allerta",
            kind = RuleElementKind.ORIGIN_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Competenza in iniziativa. Quando tiri per l'iniziativa, puoi aggiungere il bonus di competenza del
                personaggio al risultato del tiro.

                Scambio di iniziativa. Subito dopo aver tirato per l'iniziativa, puoi scambiare il risultato ottenuto
                con quello di un alleato consenziente durante il medesimo combattimento. Se il tuo personaggio o il
                suo alleato è incapacitato, non è possibile eseguire lo scambio.
            """.trimIndent(),
            prerequisite = "Nessuno",
            sourcePage = 98,
            activation = "Passiva; subito dopo il tiro per l'iniziativa",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:origin:iniziato-alla-magia",
            name = "Iniziato alla magia",
            kind = RuleElementKind.ORIGIN_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Due trucchetti. Il personaggio apprende due trucchetti a scelta tratti dalla lista degli incantesimi
                da chierico, druido o mago. La caratteristica da incantatore per gli incantesimi di questo talento
                può essere Intelligenza, Saggezza o Carisma (scegli la caratteristica quando ottieni questo talento).

                Incantesimo di 1º livello. Scegli un incantesimo di 1º livello dalla stessa lista da cui hai selezionato
                i trucchetti forniti da questo talento. Tale incantesimo è sempre considerato come preparato. Il
                personaggio può lanciarlo una volta senza consumare uno slot incantesimo e ne recupera l'utilizzo in
                questo modo dopo aver completato un riposo lungo. Può anche lanciare l'incantesimo usando uno
                qualsiasi degli slot incantesimo a sua disposizione.

                Cambio incantesimo. Quando il personaggio ottiene un nuovo livello, puoi sostituire uno degli
                incantesimi scelti per questo talento con un altro dello stesso livello dalla lista prescelta.

                Ripetibile. Questo talento è ottenibile più di una volta, ma devi scegliere una lista degli incantesimi
                diversa a ogni selezione.
            """.trimIndent(),
            prerequisite = "Nessuno",
            sourcePage = 98,
            activation = "Come l'incantesimo lanciato",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:general:aumento-punteggi-caratteristica",
            name = "Aumento dei punteggi di caratteristica",
            kind = RuleElementKind.GENERAL_FEAT,
            description = """
                Aumenta un punteggio di caratteristica a sua scelta di 2, oppure aumenta due punteggi di caratteristica
                di 1. Questo talento non può incrementare un punteggio di caratteristica oltre il 20.

                Ripetibile. Questo talento è ottenibile più di una volta.
            """.trimIndent(),
            prerequisite = "4º livello o superiore",
            sourcePage = 98,
            activation = "Passiva",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:general:lottatore",
            name = "Lottatore",
            kind = RuleElementKind.GENERAL_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Incremento dei punteggi di caratteristica. Il suo punteggio di Forza o Destrezza aumenta di 1, fino
                a un massimo di 20.

                Colpisci e afferra. Quando il personaggio colpisce una creatura con un colpo senz'armi come parte di
                un'azione di Attacco nel proprio turno, può usare sia l'opzione Danni che Presa. Questo beneficio
                è utilizzabile una sola volta per turno.

                Attacco con vantaggio. Dispone di vantaggio ai tiri per colpire contro le creature che ha afferrato.

                Lottatore rapido. Il personaggio non deve spendere alcun movimento extra se sposta una creatura
                che ha afferrato che sia della sua stessa categoria di taglia o inferiore.
            """.trimIndent(),
            prerequisite = "4º livello o superiore; Forza o Destrezza 13 o superiore",
            sourcePage = 98,
            activation = "Passiva; una volta per turno per Colpisci e afferra",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:fighting-style:armi-possenti",
            name = "Combattere con armi possenti",
            kind = RuleElementKind.FIGHTING_STYLE_FEAT,
            description = """
                Quando tiri per i danni per un attacco effettuato con un'arma da mischia che il personaggio impugna
                a due mani, se il risultato ottenuto è 1 o 2, puoi invece considerarlo come un 3. L'arma deve possedere
                la proprietà a due mani o versatile affinché ottenga questo beneficio.
            """.trimIndent(),
            prerequisite = "Privilegio Stile di combattimento",
            sourcePage = 99,
            activation = "Passiva",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:fighting-style:due-armi",
            name = "Combattere con due armi",
            kind = RuleElementKind.FIGHTING_STYLE_FEAT,
            description = """
                Quando il personaggio effettua un attacco extra come risultato dell'uso di un'arma leggera, puoi
                aggiungere il suo modificatore di caratteristica al danno di quell'attacco a patto che non sia già
                stato aggiunto in altro modo.
            """.trimIndent(),
            prerequisite = "Privilegio Stile di combattimento",
            sourcePage = 99,
            activation = "Passiva",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:fighting-style:difesa",
            name = "Difesa",
            kind = RuleElementKind.FIGHTING_STYLE_FEAT,
            description = """
                Finché il personaggio indossa un'armatura leggera, media o pesante, ottiene un +1 alla Classe Armatura.
            """.trimIndent(),
            prerequisite = "Privilegio Stile di combattimento",
            sourcePage = 99,
            activation = "Passiva",
            effects = listOf(
                RuleEffect(
                    target = EffectTarget.ARMOR_CLASS,
                    amount = 1,
                    condition = EffectCondition.WEARING_ARMOR,
                    source = "Difesa",
                ),
            ),
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:fighting-style:tiro",
            name = "Tiro",
            kind = RuleElementKind.FIGHTING_STYLE_FEAT,
            description = "Il personaggio ottiene un bonus di +2 ai tiri per colpire che effettua con le armi a distanza.",
            prerequisite = "Privilegio Stile di combattimento",
            sourcePage = 99,
            activation = "Passiva",
            effects = listOf(
                RuleEffect(
                    target = EffectTarget.RANGED_ATTACK,
                    amount = 2,
                    source = "Tiro",
                ),
            ),
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:epic-boon:dono-fato",
            name = "Dono del fato",
            kind = RuleElementKind.EPIC_BOON_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Incremento dei punteggi di caratteristica. Il punteggio di una sua caratteristica a scelta aumenta
                di 1, fino a un massimo di 30.

                Fato migliorato. Quando il personaggio o un'altra creatura entro 18 metri da sé supera o fallisce
                una prova con d20, può tirare 2d4 e applicare il risultato ottenuto come bonus o penalità a tale prova
                con d20. Una volta utilizzato questo beneficio, non può più utilizzarlo finché non tira per l'iniziativa
                o non completa un riposo breve o lungo.
            """.trimIndent(),
            prerequisite = "19º livello o superiore",
            sourcePage = 99,
            activation = "Nessuna azione; quando una creatura entro 18 metri supera o fallisce una prova con d20",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:epic-boon:dono-vista-pura",
            name = "Dono della vista pura",
            kind = RuleElementKind.EPIC_BOON_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Incremento dei punteggi di caratteristica. Il punteggio di una sua caratteristica a scelta aumenta
                di 1, fino a un massimo di 30.

                Vista pura. Il personaggio ottiene vista pura con un raggio di 18 metri.
            """.trimIndent(),
            prerequisite = "19º livello o superiore",
            sourcePage = 99,
            activation = "Passiva",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:epic-boon:dono-abilita-combattimento",
            name = "Dono delle abilità di combattimento",
            kind = RuleElementKind.EPIC_BOON_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Incremento dei punteggi di caratteristica. Il punteggio di una sua caratteristica a scelta aumenta
                di 1, fino a un massimo di 30.

                Mira impareggiabile. Quando il tiro per colpire del personaggio non va a segno, è possibile colpire
                comunque il bersaglio. Una volta sfruttato questo beneficio, non può essere riutilizzato fino all'inizio
                del turno successivo del personaggio.
            """.trimIndent(),
            prerequisite = "19º livello o superiore",
            sourcePage = 99,
            activation = "Nessuna azione; quando un tiro per colpire non va a segno",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:epic-boon:dono-offensiva-irresistibile",
            name = "Dono dell'offensiva irresistibile",
            kind = RuleElementKind.EPIC_BOON_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Incremento dei punteggi di caratteristica. Il suo punteggio di Forza o Destrezza aumenta di 1, fino
                a un massimo di 30.

                Ignora difese. I danni contundenti, perforanti e taglienti inflitti dal personaggio ignorano sempre
                la resistenza.

                Colpo soverchiante. Quando tira per colpire con il d20 e ottiene un 20, il personaggio può infliggere
                una quantità di danni extra al bersaglio pari al punteggio di caratteristica incrementato da questo
                talento. Il danno aggiuntivo è dello stesso tipo di quello dell'attacco.
            """.trimIndent(),
            prerequisite = "19º livello o superiore",
            sourcePage = 99,
            activation = "Passiva; quando un tiro per colpire con il d20 ottiene 20",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:epic-boon:dono-spirito-notturno",
            name = "Dono dello spirito notturno",
            kind = RuleElementKind.EPIC_BOON_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Incremento dei punteggi di caratteristica. Il punteggio di una sua caratteristica a scelta aumenta
                di 1, fino a un massimo di 30.

                Fusione con le ombre. Finché si trova in un'area di oscurità o luce fioca, può diventare invisibile
                come azione bonus. Tale condizione termina subito dopo che il personaggio effettua un'azione,
                un'azione bonus o una reazione.

                Forma d'ombra. Finché si trova in un'area di oscurità o luce fioca, ha resistenza a tutti i tipi
                di danno, tranne quelli psichici e radiosi.
            """.trimIndent(),
            prerequisite = "19º livello o superiore",
            sourcePage = 99,
            activation = "Azione bonus (Fusione con le ombre); passiva (Forma d'ombra)",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:epic-boon:dono-richiamo-incantesimi",
            name = "Dono del richiamo degli incantesimi",
            kind = RuleElementKind.EPIC_BOON_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Incremento dei punteggi di caratteristica. Il suo punteggio di Intelligenza, Saggezza o Carisma
                aumenta di 1, fino a un massimo di 30.

                Lancio libero. Quando lancia un incantesimo con uno slot di livello da 1 a 4, tira 1d4. Se il risultato
                corrisponde al livello dello slot, questo non verrà consumato.
            """.trimIndent(),
            prerequisite = "19º livello o superiore; privilegio Incantesimi",
            sourcePage = 100,
            activation = "Nessuna azione; quando lancia un incantesimo con uno slot di livello da 1 a 4",
        ),
        RuleElementDefinition(
            id = "srd521-it:feat:epic-boon:dono-viaggio-dimensionale",
            name = "Dono del viaggio dimensionale",
            kind = RuleElementKind.EPIC_BOON_FEAT,
            description = """
                Il personaggio ottiene i seguenti benefici.

                Incremento dei punteggi di caratteristica. Il punteggio di una sua caratteristica a scelta aumenta
                di 1, fino a un massimo di 30.

                Passi fulminei. Subito dopo che il personaggio effettua un'azione di Attacco o Magia, può
                teletrasportarsi di massimo 9 metri in uno spazio libero che è in grado di vedere.
            """.trimIndent(),
            prerequisite = "19º livello o superiore",
            sourcePage = 100,
            activation = "Nessuna azione; subito dopo un'azione di Attacco o Magia",
        ),
    )

    private val italianActions: List<RuleElementDefinition> = listOf(
        commonAction(
            id = "attacco",
            name = "Attacco",
            sourcePage = 204,
            description = """
                Quando il personaggio effettua l'azione di Attacco, può effettuare un tiro per colpire con un'arma
                o con un colpo senz'armi.

                Equipaggiarsi con le armi e riporle. Quando il personaggio effettua l'azione di Attacco come parte
                di questa azione, può anche equipaggiarsi con un'arma o riporla, decidendo se farlo prima o dopo
                l'attacco. Anche se il personaggio si equipaggia con un'arma prima di un attacco, non deve
                necessariamente usarla per quell'attacco. Equipaggiarsi con un'arma significa estrarla da un fodero
                o raccoglierla. Riporre un'arma significa rinfoderarla, metterla da parte o lasciarla cadere.

                Muoversi tra un attacco e l'altro. Se nel suo turno il personaggio si muove e ha un privilegio come,
                ad esempio, Attacco extra, questo gli fornisce la possibilità di effettuare più di un attacco come
                parte dell'azione di Attacco: il personaggio può quindi utilizzare (in parte o interamente) il
                movimento che ha a disposizione per muoversi tra un attacco e l'altro.
            """.trimIndent(),
        ),
        commonAction(
            id = "scatto",
            name = "Scatto",
            sourcePage = 214,
            description = """
                Quando il personaggio effettua l'azione di Scatto, ottiene del movimento extra per il turno in corso.
                Questo incremento è pari alla sua velocità, dopo avere applicato eventuali modificatori. Per esempio,
                se il personaggio ha una velocità di 9 metri, nel suo turno può muoversi fino a 18 metri se scatta.
                Se il personaggio ha una velocità di 9 metri che viene ridotta a 4,5 metri, nel suo turno può muoversi
                fino a 9 metri se scatta.

                Se il personaggio possiede una velocità speciale, come una velocità di volo o di nuoto, può usarla
                in sostituzione alla sua velocità quando effettua questa azione. Ogni volta che effettua l'azione,
                può scegliere quale velocità utilizzare. Vedi anche "Velocità".
            """.trimIndent(),
        ),
        commonAction(
            id = "disimpegno",
            name = "Disimpegno",
            sourcePage = 207,
            description = """
                Se il personaggio effettua un'azione di Disimpegno, il suo movimento non provoca attacchi di
                opportunità per il resto del turno in corso.
            """.trimIndent(),
        ),
        commonAction(
            id = "schivata",
            name = "Schivata",
            sourcePage = 215,
            description = """
                Se il personaggio effettua l'azione di Schivata, ottiene i seguenti benefici: fino all'inizio del suo
                turno successivo, ogni tiro per colpire effettuato contro il personaggio subisce svantaggio se il
                personaggio è in grado di vedere l'attaccante, e ogni suo tiro salvezza su Destrezza dispone di
                vantaggio. Il personaggio perde questi benefici se è incapacitato o se la sua velocità scende a 0.
            """.trimIndent(),
        ),
        commonAction(
            id = "aiutare",
            name = "Aiutare",
            sourcePage = 203,
            description = """
                Quando il personaggio effettua l'azione di Aiuto, può scegliere di fare una di queste cose.

                Aiuto a una prova di caratteristica. Il personaggio sceglie una delle competenze in un'abilità (o in
                uno strumento) e un alleato sufficientemente vicino da poter aiutare verbalmente o fisicamente quando
                questi effettua una prova di caratteristica. L'alleato dispone di vantaggio alla successiva prova di
                caratteristica che effettua con l'abilità o lo strumento scelto. Questo beneficio termina se l'alleato
                non lo utilizza prima dell'inizio del turno successivo del personaggio. Il GM ha l'ultima parola sulla
                possibilità dell'aiuto da parte del personaggio.

                Aiuto a un tiro per colpire. Il personaggio distrae momentaneamente un nemico entro 1,5 metri da sé,
                fornendo vantaggio al tiro per colpire successivo di uno dei suoi alleati contro il nemico. Questo
                beneficio termina all'inizio del turno successivo del personaggio.
            """.trimIndent(),
        ),
        commonAction(
            id = "nascondersi",
            name = "Nascondersi",
            sourcePage = 210,
            description = """
                Con l'azione di Nascondersi, il personaggio cerca di nascondersi. Per farlo, deve superare una prova
                di Destrezza (Furtività) con CD 15 mentre si trova in un'area pesantemente oscurata o dietro tre
                quarti di copertura o dietro copertura totale; inoltre, deve essere fuori dal campo visivo di qualsiasi
                nemico. Se il personaggio è in grado di vedere una creatura, può capire se anch'essa è in grado di
                vederlo.

                Se la prova viene superata, il personaggio ha la condizione "invisibile" finché resta nascosto.
                Prendi nota del risultato totale della prova: questo costituisce la CD contro cui la creatura deve
                superare una prova di Saggezza (Percezione) per trovare il personaggio.

                Il personaggio non è più nascosto quando si verifica uno dei seguenti eventi: il personaggio emette
                un suono più forte di un sussurro, effettua un tiro per colpire, lancia un incantesimo con una
                componente verbale o viene trovato da un nemico.
            """.trimIndent(),
        ),
        commonAction(
            id = "influenzare",
            name = "Influenzare",
            sourcePage = 208,
            description = """
                Con l'azione di Influenza, il personaggio esorta un mostro a fare qualcosa. Descrivi o interpreta
                il modo in cui interagisce con il mostro: il personaggio cerca di ingannarlo, intimidirlo, intrattenerlo
                o persuaderlo gentilmente? Il GM determina se, dopo l'interazione, il mostro si sente ben disposto
                o meno verso il personaggio, o se è esitante. In base a ciò, si stabilisce se sia necessaria una prova
                di caratteristica, come spiegato di seguito.

                Ben disposto. Se l'esortazione del personaggio si allinea con i desideri del mostro, non è necessaria
                alcuna prova di caratteristica: il mostro soddisfa la richiesta del personaggio nel modo che preferisce.

                Maldisposto. Se l'esortazione del personaggio è sgradita al mostro o è contraria al suo allineamento,
                non è necessaria alcuna prova di caratteristica: il mostro si rifiuta di soddisfare la richiesta.

                Esitante. Se il personaggio esorta il mostro a fare qualcosa ma questo si mostra esitante, il
                personaggio deve effettuare una prova di caratteristica influenzata dall'atteggiamento del mostro:
                indifferente, amichevole, ostile (ciascuno dei quali è definito nel glossario). Il GM sceglie la prova
                da effettuare, la quale ha una CD predefinita pari a 15 o pari al punteggio di Intelligenza del mostro
                (si sceglie il valore più alto tra i due). Se la prova viene superata, il mostro esegue la richiesta del
                personaggio. In caso di fallimento, il personaggio deve aspettare 24 ore (o un intervallo di tempo
                definito dal GM) prima di esortare di nuovo il mostro allo stesso modo.

                Prove di Influenza. Carisma (Inganno): ingannare un mostro che capisce il personaggio. Carisma
                (Intimidire): intimidire un mostro. Carisma (Intrattenere): intrattenere un mostro. Carisma
                (Persuasione): persuadere un mostro che capisce il personaggio. Saggezza (Addestrare animali):
                persuadere gentilmente una bestia o una mostruosità.
            """.trimIndent(),
        ),
        commonAction(
            id = "magia",
            name = "Magia",
            sourcePage = 209,
            description = """
                Quando il personaggio esegue l'azione di Magia, lancia un incantesimo che ha il tempo di lancio di
                un'azione, oppure usa un privilegio o un oggetto magico che richiede un'azione di Magia per essere
                attivato.

                Se il personaggio lancia un incantesimo che ha un tempo di lancio di 1 minuto o più, deve effettuare
                un'azione di Magia in ogni turno del lancio e, allo stesso tempo, deve mantenere la concentrazione.
                Se la sua concentrazione viene interrotta, l'incantesimo fallisce, ma il personaggio non consuma uno
                slot incantesimo. Vedi anche "Concentrazione".
            """.trimIndent(),
        ),
        commonAction(
            id = "prepararsi",
            name = "Prepararsi",
            sourcePage = 211,
            description = """
                Il personaggio effettua l'azione di Prepararsi nell'attesa che si verifichi una data circostanza prima
                di agire. A tale scopo, nel suo turno il personaggio effettua questa azione, che gli consente di agire
                effettuando la sua reazione prima dell'inizio del suo turno successivo.

                Per prima cosa, il giocatore deve decidere quale circostanza percettibile innescherà la sua reazione.
                Poi deve scegliere quale azione effettuerà in risposta a quell'innesco, oppure può decidere di muoversi
                al massimo della sua velocità. Alcuni esempi includono: "Se il cultista passa sopra la botola, tiro la
                leva che la apre" e "Se lo zombi si avvicina a me, mi muovo per allontanarmi".

                Quando l'innesco si verifica, il personaggio può scegliere se eseguire la sua reazione non appena
                l'innesco si è concluso o se ignorarlo.

                Quando il personaggio prepara un incantesimo, lo lancia normalmente (spendendo le risorse utilizzate
                per lanciarlo) ma trattiene la sua energia, liberandola con la sua reazione quando l'innesco si verifica.
                Al fine di poter essere preparato, un incantesimo deve avere il tempo di lancio di 1 azione; inoltre,
                trattenere la magia dell'incantesimo richiede concentrazione, che il personaggio riesce a mantenere
                fino all'inizio del suo turno successivo. Se la concentrazione viene interrotta, l'incantesimo si
                dissipa senza avere effetto.
            """.trimIndent(),
        ),
        commonAction(
            id = "cercare",
            name = "Cercare",
            sourcePage = 212,
            description = """
                Quando il personaggio effettua l'azione di Ricerca, effettua una prova di Saggezza per discernere
                qualcosa che non sia ovvio. La tabella Cercare indica quali abilità sono applicabili quando si usa
                questa azione, in base a ciò che il personaggio sta cercando di individuare.

                Intuizione: stato mentale di una creatura.
                Medicina: malattia o causa di morte di una creatura.
                Percezione: creature o oggetti nascosti.
                Sopravvivenza: tracce di cibo.
            """.trimIndent(),
        ),
        commonAction(
            id = "studiare",
            name = "Studiare",
            sourcePage = 216,
            description = """
                Quando il personaggio effettua l'azione di Studio, effettua una prova di Intelligenza per analizzare
                la propria memoria, un libro, un indizio o un'altra fonte di conoscenza, richiamando alla mente
                un'informazione importante a riguardo. La tabella Aree di conoscenza indica quali abilità sono
                applicabili alle varie aree di conoscenza.

                Arcano: incantesimi, oggetti magici, simboli occulti, tradizioni magiche, piani di esistenza e alcune
                creature (aberrazioni, costrutti, elementali, folletti e mostruosità).
                Indagare: trappole, cifrari, indovinelli e congegni.
                Natura: terreno, flora, tempo atmosferico e alcune creature (bestie, draghi, melme e vegetali).
                Religione: divinità, riti e gerarchie religiose, simboli sacri, culti e alcune creature (celestiali,
                immondi e non morti).
                Storia: eventi e personaggi storici, antiche civiltà, guerre e alcune creature (giganti e umanoidi).
            """.trimIndent(),
        ),
        commonAction(
            id = "utilizzare",
            name = "Utilizzare",
            sourcePage = 218,
            description = """
                Di norma un personaggio interagisce con un oggetto mentre fa qualcos'altro, come per esempio quando
                sfodera una spada come parte dell'azione di Attacco. Quando l'utilizzo di un oggetto richiede un'azione,
                il personaggio effettua l'azione di Utilizzo.
            """.trimIndent(),
        ),
    )

    /** Compatibilita': l'edizione italiana resta il riferimento delle prove. */
    val feats: List<RuleElementDefinition> get() = italianFeats
    val actions: List<RuleElementDefinition> get() = italianActions
    val all: List<RuleElementDefinition> get() = all(AppLanguage.ITALIAN)

    fun feats(language: AppLanguage): List<RuleElementDefinition> = when (language) {
        AppLanguage.ITALIAN -> italianFeats
        AppLanguage.ENGLISH -> englishFeats
    }

    fun actions(language: AppLanguage): List<RuleElementDefinition> = when (language) {
        AppLanguage.ITALIAN -> italianActions
        AppLanguage.ENGLISH -> englishActions
    }

    fun all(language: AppLanguage = AppLanguage.ITALIAN): List<RuleElementDefinition> =
        feats(language) + actions(language)

    /**
     * I talenti inglesi non si traducono a mano: nome e testo arrivano dal PDF
     * inglese, gia' estratto in `feats.json`, e il crosswalk dice quale voce
     * corrisponde a quale. Le regole — effetti, scelte, prerequisiti — restano
     * quelle italiane, perche' sono le stesse regole.
     */
    private val englishFeats: List<RuleElementDefinition> by lazy {
        val source = SrdFeatSource.byItalianName(AppLanguage.ENGLISH)
        italianFeats.map { feat ->
            val record = source.getValue(feat.name)
            feat.copy(
                name = record.name,
                description = record.description,
                prerequisite = record.prerequisite.ifBlank { "None" },
                activation = ENGLISH_FEAT_ACTIVATIONS.getValue(feat.activation),
                effects = feat.effects.map { effect -> effect.copy(source = record.name) },
            )
        }
    }

    private val englishActions: List<RuleElementDefinition> by lazy {
        italianActions.map { action ->
            val text = ENGLISH_ACTIONS.getValue(action.id.substringAfterLast(':'))
            action.copy(
                name = text.name,
                description = text.description,
                prerequisite = "None",
                activation = "Action",
            )
        }
    }
}

private val ENGLISH_FEAT_ACTIVATIONS = mapOf(
    "Passiva" to "Passive",
    "Nessuna azione; una volta per turno, quando colpisce con un'arma" to
        "No action; once per turn, when you hit with a weapon",
    "Passiva; subito dopo il tiro per l'iniziativa" to
        "Passive; immediately after rolling Initiative",
    "Come l'incantesimo lanciato" to "Same as the spell cast",
    "Passiva; una volta per turno per Colpisci e afferra" to
        "Passive; once per turn for Punch and Grab",
    "Nessuna azione; quando una creatura entro 18 metri supera o fallisce una prova con d20" to
        "No action; when a creature within 60 feet succeeds or fails a D20 Test",
    "Nessuna azione; quando un tiro per colpire non va a segno" to
        "No action; when an attack roll misses",
    "Passiva; quando un tiro per colpire con il d20 ottiene 20" to
        "Passive; when a d20 attack roll scores 20",
    "Azione bonus (Fusione con le ombre); passiva (Forma d'ombra)" to
        "Bonus Action (Merge with Shadows); passive (Shadow Form)",
    "Nessuna azione; quando lancia un incantesimo con uno slot di livello da 1 a 4" to
        "No action; when you cast a spell using a level 1–4 spell slot",
    "Nessuna azione; subito dopo un'azione di Attacco o Magia" to
        "No action; immediately after taking the Attack or Magic action",
)

// Con `get()`, non con un valore: le liste dell'oggetto si costruiscono usando
// funzioni di primo livello di questo stesso file, quindi inizializzare qui una
// proprieta' leggendo `SrdFeatsAndActions.all` la valuterebbe mentre l'oggetto
// e' ancora a meta' della propria inizializzazione, e troverebbe liste nulle.

/** Nome breve usato dall'aggregatore del pacchetto SRD. */
val srdFeatsAndActions: List<RuleElementDefinition> get() = SrdFeatsAndActions.all

/** Nome descrittivo mantenuto come API pubblica del catalogo. */
val srdFeatsAndCommonActions: List<RuleElementDefinition> get() = SrdFeatsAndActions.all

private fun commonAction(
    id: String,
    name: String,
    sourcePage: Int,
    description: String,
): RuleElementDefinition = RuleElementDefinition(
    id = "srd521-it:action:$id",
    name = name,
    kind = RuleElementKind.COMMON_ACTION,
    description = description,
    prerequisite = "Nessuno",
    sourcePage = sourcePage,
    activation = "Azione",
)
