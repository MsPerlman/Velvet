package li.velvetchat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * the app's texts, one language per object
 *
 * why an interface and not a translation file
 *
 * the french was written into the screens, which works while everybody speaks it and stops working
 * the day somebody does not. that left the form to choose
 *
 * a `key -> text` table (a `.properties` file, `strings.xml`, json) is the usual answer, and it has
 * one flaw you pay for a long time: nothing says a key is missing. you find out on screen, in the
 * wrong language, on the phone of the person concerned. here each text is an interface property, so
 * adding a sentence to the french stops compiling until the english has it. the compiler keeps the
 * list for us
 *
 * texts with holes are functions (`membresEnLigne(12)`), which settles word order at the same time,
 * since a positional placeholder does not always survive the trip between languages
 *
 * how it gets used
 *
 * ```kotlin val t = textes() Text(t.seConnecter) ```
 *
 * `LocalLangue` is provided once at each app's root, and changing language recomposes everything
 * that reads `textes()`, with no restart
 *
 * outside the composition
 *
 * a notification, a foreground service, a date format do not go through compose, so not through
 * `LocalLangue`. they read [horsUi], which each app root keeps up to date. that is the only breach
 * and it is deliberate: the alternative was threading a language down into a
 * `NotificationCompat.Builder`
 *
 * what is not in here
 *
 * everything still in french in the code is technical text: traces, exception messages, log labels,
 * bits of windows script. nobody reads them except to debug, and translating them would muddle a
 * search through the logs
 */
interface Traduction {

    val connexionTitre: String
    val connexionSousTitre: String
    val champServeur: String
    val champIdentifiant: String
    val champMotDePasse: String
    val seConnecter: String
    val connexionImpossible: String
    val avertissementVerification: String
    val pasDeCompte: String
    val continuerNavigateur: String
    val explicationNavigateur: String
    val attenteNavigateur: String
    val codeCopie: String
    val activerAdministration: String
    val explicationAdministration: String

    // settings
    val parametres: String
    val compte: String
    val seDeconnecter: String
    val confirmerDeconnexion: String
    val deconnexionExplication: String
    val application: String
    val chercherMiseAJour: String
    val rechercheEnCours: String
    val langue: String
    val langueFrancais: String
    val langueAnglais: String

    // ---------------------------------------------------------------------------- import
    val importTitre: String
    val importPaquet: String
    val importConversations: String
    val importOptions: String
    val importMedias: String
    val importMediasDetail: String
    val importDu: String
    val importAu: String
    val importSalonCible: String
    val importChercher: String
    val importAucunPaquet: String
    val importLecture: String
    val importEnCours: String
    val importTermine: String
    val importInterrompu: String
    val importArrete: String
    val arreter: String
    val importChoisirDabord: String
    fun importLancer(nombre: Int): String
    fun importAuNomDe(qui: String): String
    fun importMessagesPieces(messages: Int, pieces: Int): String
    fun importMessages(messages: Int): String

    val ficheSurnom: String
    val ficheNote: String
    val ficheNoteInvite: String
    val ficheAPropos: String

    /** the elapsed counter under an activity. [duree] is already formatted as MM:SS or H:MM:SS */
    fun ficheActiviteEcoulee(duree: String): String
    val ficheMessagePrive: String
    val ficheMentionner: String
    val ficheAjouterAmi: String
    val ficheRetirerAmi: String
    val ficheBloquer: String
    val ficheDebloquer: String
    val confirmerLeBlocage: String
    val ficheConfirmerBlocage: String
    val ficheModerer: String
    val joindre: String
    val joindrePhotos: String
    val joindrePhotosDetail: String
    val joindreAppareil: String
    val joindreAppareilDetail: String
    val joindreFichier: String
    val joindreFichierDetail: String
    val joindreSondage: String
    val joindreSondageDetail: String
    val statutEnLigne: String
    val statutAbsent: String
    val statutInactif: String
    val statutNePasDeranger: String
    val statutInvisible: String
    val statutHorsLigne: String
    val statutInconnu: String
    val statutEnLigneTitre: String
    val statutPersonnalise: String
    val statutInvite: String
    val statutEffacer: String
    val enregistrer: String
    val quitterSansEnregistrer: String
    val abandonnerLesModifications: String
    val mpTitre: String
    val mpAucune: String
    val mpNouvelle: String
    val mpAjouterAmis: String
    val invitationAccepter: String
    val invitationRefuser: String

    val composerInvite: String
    val envoyer: String
    val emojis: String
    val messageVocal: String
    val dicter: String
    val dicteeArreter: String
    val dicteeEnCours: String
    val dicteeEnPause: String
    val dicteeInterrompue: String
    val dicteeTranscription: String
    val dicteeTerminer: String
    val dicteePause: String
    val dicteeReprendre: String
    val dicteeAnnuler: String
    val repondre: String
    val annulerReponse: String
    val chercherDansSalon: String
    val effacerRecherche: String
    val aucunSalon: String
    val aucunSalonSelectionne: String
    val serveurSansSalon: String
    val bienvenue: String
    val ajouterServeur: String
    val creerSalon: String
    val horsLigneReessai: String
    val connexionEnCours: String
    val conversationIndisponible: String
    val choisirGalerie: String
    val prendrePhoto: String
    val banniere: String
    val photoDeProfil: String
    val iconeServeur: String
    val dejaLu: String

    val copier: String
    val copie: String
    val creer: String
    val echec: String
    val echecMinuscule: String
    val arreteMinuscule: String
    val plusTard: String
    val reessayer: String
    val apercu: String
    val rechercher: String
    val ouverture: String
    val lectureImpossible: String
    val imageIllisible: String
    val fichierIllisible: String
    val gifIllisible: String
    val ajoutImpossible: String

    val marquerCommeLu: String
    val deplacerVers: String
    val historiqueChiffre: String
    val messageSupprime: String
    val messageSupprimeMinuscule: String
    val messageChiffreIllisible: String
    val modifie: String
    val modifierLeMessage: String
    val supprimerLeMessage: String
    val copierLeTexte: String
    val plusDEmojis: String
    val medias: String
    val video: String
    val nouveauMessage: String
    val mentionsActivees: String
    val mentionsCoupees: String
    val ecrireA: String
    val ecrire: String
    val toutEstLu: String
    val toutMarquerLu: String
    val tousLesMessages: String
    val utiliserCategorieParDefaut: String
    val nouveauFil: String
    val fermerLeFil: String
    val nomDuFil: String
    val lectureDesFils: String
    val fermerLaRecherche: String
    val masquerLesMembres: String
    val afficherLesMembres: String
    val masquerLeProfil: String
    val afficherLeProfil: String
    val voirProfilComplet: String
    val reduireLaBio: String
    val afficherLaBioComplete: String
    val blocageImpossible: String
    fun aujourdhuiA(heure: String): String
    fun hierA(heure: String): String
    fun membresNombre(nombre: Int): String
    fun resultatsNombre(nombre: Int): String
    fun invitationDePar(qui: String): String
    fun invitationDe(qui: String): String

    // the thread's system sentences
    val sysQuelquun: String
    fun sysRejoint(qui: String): String
    fun sysParti(qui: String): String
    fun sysInvite(qui: String, cible: String): String
    fun sysInvitationAcceptee(qui: String): String
    fun sysInvitationRefusee(qui: String): String
    fun sysInvitationRetiree(qui: String, cible: String): String
    fun sysExclu(qui: String, cible: String): String
    fun sysBanni(qui: String, cible: String): String
    fun sysDebanni(qui: String, cible: String): String
    fun sysDemandeEntrer(qui: String): String
    fun sysSalonRenomme(qui: String, nom: String?): String
    fun sysSujetChange(qui: String): String
    val sysChiffrementActif: String
    val sysSalonRemplace: String
    fun sysAppelLance(qui: String): String

    val modifierLeProfil: String
    val themeDeProfil: String
    val effetDeProfil: String
    val cadreDeProfil: String
    val effetEtCadre: String
    val nomDAffichage: String
    val pseudoDAffichage: String
    val pseudoPremiereCouleur: String
    val pseudoSecondeCouleur: String
    val themeCouleurHaut: String
    val themeCouleurBas: String
    val aProposDeMoi: String
    val portee: String
    val laVotre: String
    val leVotre: String
    val votreCadre: String
    val degrade: String
    val themeMenu: String
    fun themeEst(nom: String): String
    fun surTotal(affiches: Int, total: Int): String

    val parametresDuServeur: String
    val profilDuServeur: String
    val photoDuServeur: String
    val modifierProfilParServeur: String
    val tagDuServeur: String
    val tagRetire: String
    val tagViaReglagesServeur: String
    val nomDuServeurMaj: String
    val lienInvitationMaj: String
    val envoieCeLienMaj: String
    val particularites: String
    val lectureDuProfil: String
    val creerLaCategorie: String
    val visibleParTousLesMembres: String
    val emoji: String
    val emojiFacultatifMaj: String
    val roles: String
    val rolesDecoratifs: String
    val nouveauRole: String
    val nomDuRole: String
    val couleurDuRole: String
    val retirerLeRole: String
    val quiPorteCeRole: String
    val roleDistribuePlusTard: String
    fun etapeSur(etape: Int, total: Int): String
    val serveurExemple: String
    fun profilServeurIllisible(quoi: String): String

    // moderation and administration
    val exclureDuServeur: String
    val nouveauPalier: String
    val moderateur: String
    val glypheFacultatif: String
    val niveauDePouvoir: String
    val usagesIllimites: String
    val invitationEpuisee: String
    val copierLeCode: String
    val revoquer: String
    val desactiver: String
    val desactive: String
    val reactiver: String
    val comptesIllisibles: String
    fun desactiverQui(qui: String): String
    fun expireLe(date: String): String

    val appelVocal: String
    val appelImpossible: String
    val replierLAppel: String
    val vousSeul: String
    val couperLeMicro: String
    val retablirLeMicro: String
    val arreterLePartage: String
    val partageImpossible: String
    fun appelAvec(qui: String): String
    val voixEtVideo: String
    val peripheriques: String
    val peripheriqueDeSortie: String
    val coupe: String
    val retablir: String

    // verification and encryption
    val appareilVerifie: String
    val historiqueAccessible: String
    val premierAppareil: String
    val verifierCetAppareil: String
    val sessionVerifiee: String
    val sessionNonVerifiee: String
    val cetAppareil: String
    val verifierMaintenant: String
    val verifier: String
    val pressePapierVide: String
    val cleRefusee: String
    val motDePasseDuCompte: String

    // app settings
    val parametresUtilisateurMaj: String
    val parametresApplicationMaj: String
    val parametresUtilisateur: String
    val nomDUtilisateur: String
    val theme: String
    val themeActuel: String
    val parDefaut: String
    val miseAJour: String
    val miseAJourImpossible: String
    val rechercheImpossible: String
    val velvetAJour: String
    val velvetDemarre: String
    val demarrage: String
    fun demarrageImpossible(quoi: String): String
    val publierMaFrappe: String
    val probleme: String
    val rapportSujet: String
    val aProposTitre: String
    fun stockageMoteur(taille: String): String

    val emojisTitre: String
    val resultats: String
    val trouveEmojiParfait: String
    val ouvrirLesReglages: String
    val afficherGifRecus: String
    val cleDApi: String
    val collezLaCleIci: String
    val utiliserKlipy: String
    val rechercherSurKlipy: String
    val passerelleDiscord: String
    val passerelleDetail: String
    val emojisUtilises: String
    val importerEmojisUtilises: String

    // images and dictation
    val decoupageImpossible: String
    val imageMiseAJour: String
    val dicteeRefusee: String
    fun pageNumero(numero: Int): String

    val veilleLecture: String
    val veilleArretee: String
    val importDiscordTitre: String
    val importLectureDuPaquet: String
    val importArreteTitre: String
    fun importMediasRates(nombre: Int): String
    fun importPaquetIllisible(quoi: String): String
    fun importRefuse(quoi: String): String
    fun mpNombre(nombre: Int): String

    fun lienTexteTrompeur(texte: String): String
    val lienTypeInconnu: String

    // the rest of the interface
    val creerUnServeur: String
    val rejoindreUnServeur: String
    val lienOuAdresse: String
    val identifiantMatrix: String
    val invitationEnvoyee: String
    val invitationEchouee: String
    val copierLienInvitation: String
    val lienCopie: String
    val lienReserveAdmin: String
    val lienSansServeur: String
    val collerLienInvitation: String
    val dejaUneInvitation: String
    val reserveAdmin: String
    val ouvertureInvitationsAdmin: String
    val lienDInvitation: String
    val lienSansExpiration: String
    val lienInvitationExplication: String
    val preparationDuLien: String
    val centreDeControle: String
    val genererUneInvitation: String
    val nouvelleInvitation: String
    val invitationCreee: String
    val invitationsIllisibles: String
    val aucuneInvitationActive: String
    val genererInvitationAide: String
    val codeEstLInvitation: String
    val espaceRejointAuto: String
    val aucunEspaceAProposer: String
    val unePersonne: String
    val cinqPersonnes: String
    val septJours: String
    val trenteJours: String
    val unUsageRestant: String
    val generationEnCours: String
    val creationImpossible: String
    val desactivationExplication: String
    val chargement: String
    val rejoindreVelvet: String
    val creerCompteAvecCode: String
    val codeDInvitation: String
    val creerLeCompte: String
    val inscriptionEnCours: String
    val inscriptionImpossible: String
    val dejaUnCompte: String
    val creerSalonTextuel: String
    val creerSalonVocalTitre: String
    val creerLeSalon: String
    val modifierLeSalon: String
    val supprimerLeSalon: String
    val creerUneCategorie: String
    val categorieExplication: String
    val nomDuSalonMaj: String
    val nomDeLaCategorieMaj: String
    val rangerDans: String
    val aucuneCategorie: String
    val aucunSalonOuvert: String
    val aucuneConversationOuverte: String
    val choisirUnAGauche: String
    val choisirUneAGauche: String
    val chercherDesSalons: String
    val voirLesSalons: String
    val masquerSalonsMuets: String
    val montrerTousLesSalons: String
    val epinglerLeSalon: String
    val rendreLeSalonMuet: String
    val mettreEnSourdine: String
    val reactiverLeSon: String
    val retablirLeSon: String
    val copierIdSalon: String
    val copierIdServeur: String
    val copierIdMessage: String
    val copierLienMessage: String
    val copierIdUtilisateur: String
    val copierLeLien: String
    val identifiantCopie: String
    val quitterLeServeur: String
    val confirmerLeDepart: String
    val inviterDesGens: String
    val inviterDansLeSalon: String
    val inviterDansLeSalonVocal: String
    val inviterSurLeServeur: String
    val inviterQuelquun: String
    val creerUnEvenement: String
    val boiteDeReception: String
    val aucunMessage: String
    val serveurSansSalonAide: String
    val salonNonRejoint: String
    val choisirSalonOuPersonne: String
    val accueilVide: String
    val marquerCommeNonLu: String
    val voirLeProfil: String
    val pleinEcran: String
    val quitterPleinEcran: String
    fun partageSonEcran(nom: String): String
    fun saCamera(nom: String): String
    val sonDeLaFenetre: String
    val sonDuPcSaufVelvet: String
    val sonDeLaFenetreAide: String
    val sonSansGstreamer: String
    val fermerLeMp: String
    val supprimerLeGroupe: String
    val reactiverNotifications: String
    val sourdinePour: String
    val sourdine15Min: String
    val sourdine1Heure: String
    val sourdine8Heures: String
    val sourdine24Heures: String
    val sourdineToujours: String
    val epinglerLeMessage: String
    val desepinglerLeMessage: String
    val retirerDesEpingles: String
    val retirerDesEpinglees: String
    val epinglerEnHaut: String
    val messagesEpingles: String
    val aucunMessageEpingle: String
    val aucunMessageEpingleAide: String
    val epinglesIllisibles: String
    val chargementEpingle: String
    val lectureDesEpingles: String
    val epinglesUneParUne: String
    val appuiLongPourEpingler: String
    val ajouterUneReaction: String
    val confirmerLaSuppression: String
    val modificationDuMessage: String
    val annulerLaModification: String
    val revenirAuPresent: String
    val vousRegardezPlusAncien: String
    val revenirAuClavier: String
    val joindreUnFichier: String
    val joindreUnePhoto: String
    val envoyerUnFichier: String
    val envoyerUnMessage: String
    val autocollantNonEnvoye: String
    val gifNonEnvoye: String
    val imageProfilNonEnvoyee: String
    val chiffrementIllisible: String
    val dechiffrementImpossible: String
    val messageAvantArrivee: String
    val cleNonPartagee: String
    val cleIntrouvable: String
    val synchroArretee: String
    val echecNouvelleTentative: String
    val conversationIndisponiblePoint: String
    val aucuneConversationDesignee: String
    val conversationPlusAccessible: String
    val sessionIndisponible: String
    val creerUnFil: String
    val repondreDansUnFil: String
    val repondreDansLeFil: String
    val envoyerDansLeFil: String
    val filNonCree: String
    val aucuneReponse: String
    val personneNaRepondu: String
    val premiereReponseOuvreLeFil: String
    val nonPrisEnChargeDansUnFil: String
    val aucunFilDeDiscussion: String
    val aucunFilDeCeNom: String
    val aucunFil: String
    val rechercherNomDeFil: String
    val filExplication: String
    val appuiLongPourFil: String
    val chiffreDeBoutEnBout: String
    val aucunLienPartage: String
    val aucunMembreAAfficher: String
    val aucunePhotoNiVideo: String
    val appelEnCours: String
    val appelEntrant: String
    val voixConnectee: String
    val enAttenteDesAutres: String
    /** the voice preview sheet's button, and what it says when the room is empty */
    val rejoindreLeVocal: String
    val personneDansLeVocal: String
    val personneDautreEncore: String
    val allumerLaCamera: String
    val eteindreLaCamera: String
    val retournerLaCamera: String
    val partagerLEcran: String
    val ecran: String
    val choisirCeQuOnEnvoie: String
    val applications: String
    val ecranEntier: String
    val appareils: String
    val rienAPartager: String
    val qualiteDuPartage: String
    val partagerLeSon: String
    val aucunBouclageAudio: String
    val entreesVideo: String
    val unFichierVideo: String
    val ffmpegAbsent: String
    val partageDEcran: String
    val partageDEcranRefuse: String
    val votreEcranEstVisible: String
    val partageAvecLesParticipants: String
    val canalAppelDescription: String
    val soundboardAuCasque: String
    val reactiverLeMicro: String
    val microIndisponible: String
    val lancerAppelVocal: String
    val lancerAppelVideo: String
    val peripheriqueDEntree: String
    val aucunPeripherique: String
    val dicteeInjoignable: String
    val transcriptionImpossible: String
    val transcriptionImpossibleReessayer: String
    val transcriptionEnCours: String
    val voirLaTranscription: String
    val aucuneParoleDetectee: String
    val formatAudioNonSupporte: String
    val formatImageInconnu: String
    val apparenceDuProfil: String
    val commentOnVousVoit: String
    val quelquesLignesSurVous: String
    val parleUnPeuDeToi: String
    val profilPrincipal: String
    val plaqueNominative: String
    val decorationDAvatar: String
    val avatarEtDecoration: String
    val themeEtBanniere: String
    val styleDuPseudonyme: String
    val choisirUnAvatar: String
    val retirerLaBanniere: String
    val cliquePourAjouterNote: String
    val noteVisibleParToi: String
    val deplierLePanneau: String
    val replierLePanneau: String
    val themeDeProfilAide: String
    val tagAide: String
    val aucunTagDisponible: String
    val cadreSansCatalogue: String
    val catalogueVide: String
    val chargementDuCatalogue: String
    val rechercherNomOuCollection: String
    val nomOuCollection: String
    val sansCollection: String
    val animesDabord: String
    val parCollection: String
    val importerUneDecoration: String
    val importerPoints: String
    val changerLeStatut: String
    val apparaitraHorsLigne: String
    val pasDeNotificationsBureau: String
    val etatInconnu: String
    val ajouterEnAmi: String
    val aucunRoleAAjouter: String
    val blocageExplication: String
    val profilVisibleExplication: String
    val banniereEnClair: String
    val photoEtBanniereEnClair: String
    val gestionDesUtilisateurs: String
    val emojiEtNom: String
    val choisirUnEmoji: String
    val ajouterUneImage: String
    val ajouterLAutocollant: String
    val ajouterUnSon: String
    val ajouterUneDecoration: String
    val changerIconeServeur: String
    val supprimerLIcone: String
    val enregistrementEnCours: String
    val envoiEnCours: String
    val envoiEnCoursLong: String
    val emojisEtAutocollantsDuServeur: String
    val sonsDuServeur: String
    val decorationsDuServeur: String
    val emojisExplication: String
    val sonsExplication: String
    val choisirLePassage: String
    val ecouterLaSelection: String
    val sonTropLong: String
    val sonIllisible: String
    fun surDuree(garde: String, total: String): String
    val decorationsExplication: String
    val tagDuServeurAide: String
    val tagPorteParLesMembres: String
    val tagAfficheParLesMembres: String
    val tagEnregistre: String
    val creeTonServeur: String
    val serveurPhotoEtNom: String
    val tailleImageRecommandee: String
    val profilServeurAide: String
    val particularitesAide: String
    val descriptionServeurAide: String
    val modificationsNonEnregistrees: String
    val parametresDeNotification: String
    val parametresDeNotifications: String
    val parametresDeConfidentialite: String
    val mentionsSeulement: String
    val lectureEnCours: String
    val creerUnNouveauRole: String
    val modifierLeRole: String
    val supprimerCeRole: String
    val nouveauRoleMinuscule: String
    val roleNonEnregistre: String
    val aucunRoleDecoratif: String
    val aucunRoleAide: String
    val rolesDecoratifsExplication: String
    val roleNomEtCouleur: String
    val couleurDuPremierRole: String
    val roleDistributionAide: String
    val choisirUneCouleur: String
    val couleurLibre: String
    fun contrasteFaible(rapport: String): String
    val publicationEnCours: String
    val publicationRefusee: String
    val publierLesPaliers: String
    val plusNouveauPalier: String
    val aucunPalierDefini: String
    val palierExplication: String
    val pouvoirSuperieur: String
    val bannirDuServeur: String
    val confirmerLExclusion: String
    val confirmerLeBannissement: String
    val rendreMuet: String
    val rendreLaParole: String
    val frequemmentUtilises: String
    val autocollantsPacksSepares: String
    val aucunAutocollant: String
    val roomToutLeSalon: String
    val aucunGif: String
    val tendancesKlipy: String
    val rechercheEnvoyeeAKlipy: String
    val aucuneCleKlipyBureau: String
    val klipyExplicationBureau: String
    val afficherLesAnnonces: String
    val aucuneTendance: String
    val cleKlipyManquante: String
    val klipyExplication: String
    val annoncesExplication: String
    val rechercheGifExplication: String
    val gifRecusExplication: String
    val nouveauSondage: String
    val votreQuestion: String
    val ajouterUneReponse: String
    val retirerCetteReponse: String
    val creerLeSondage: String
    val sondageClos: String
    val bulletinSecret: String
    val bulletinSecretNote: String
    val bulletinSecretAide: String
    val personneNaVote: String
    val unVote: String
    val verificationImpossible: String
    val verificationAnnulee: String
    val verificationEchouee: String
    val verificationEnAttente: String
    val verificationEnCours: String
    val verificationPoints: String
    val demandeDeVerification: String
    val symbolesIdentiques: String
    val ilsSontIdentiques: String
    val caNeCorrespondPas: String
    val preparationComparaison: String
    val demandeAcceptee: String
    val comparerAvecLAutreAppareil: String
    val autreAppareilAInterrompu: String
    val autreAppareilPeutLire: String
    val suitesDiscordantes: String
    val ouvrirSurAppareilVerifie: String
    val refuserSiPasVous: String
    val septSymboles: String
    val verifierDepuisUnAutre: String
    val etablirCetteIdentite: String
    val identiteImpossible: String
    val cleDeRecuperation: String
    val jaiUneCle: String
    val enCours: String
    val premierAppareilExplication: String
    val sansVerificationExplication: String
    val plusTardExplication: String
    val aucuneSauvegardeActive: String
    val historiqueChiffreExplication: String
    val appareilNonVerifie: String
    val lectureDeLaSession: String
    val synchronisationEnCours: String
    val lienTrompeur: String
    val adresseInhabituelle: String
    val ouvrirQuandMeme: String
    val ecrireAQuelquun: String
    val lancerUneConversation: String
    val nouveauGroupe: String
    val groupeChoisirPersonnes: String
    val creerLeGroupe: String
    val rechercherOuLancer: String
    val mpExplication: String
    val identifiantMatrixAide: String
    val conversationNonOuverte: String
    val uneInvitation: String
    val invitationServeur: String
    val invitationConversation: String
    val invitationGroupe: String
    val accepterServeurExplication: String
    val accepterConversationExplication: String
    val aLInstant: String
    val uneReponse: String
    val uneArrivee: String
    val unDepart: String
    val unNouveauMessage: String
    val unResultat: String
    val rienTrouve: String
    val rechercheParPages: String
    val rechercheLimitee: String
    val monCompte: String
    val infosDuCompte: String
    val versionInstallee: String
    val derniereVersion: String
    val aucuneMiseAJour: String
    val bandeauMiseAJour: String
    val miseAJourParLeServeur: String
    val redemarrerMaintenant: String
    val telechargementEnCours: String
    val installationEnCours: String
    val telechargementInterrompu: String
    val telechargementImpossible: String
    val lectureImpossibleMinuscule: String
    val empreinteNonConforme: String
    val fondDEcran: String
    val fondDEcranPoints: String
    val changerLeFondDEcran: String
    val retirerLeFondDEcran: String
    val choisirUnFondDEcran: String
    val fondDEcranAide: String
    val revenirAuThemeParDefaut: String
    val fichierDeTheme: String
    val choisirUnTheme: String
    val importerUnTheme: String
    fun themeRefuse(raison: String): String
    val choisirUneImage: String
    val choisirPoints: String
    val langueAide: String
    val deconnexionBureauExplication: String
    val frappeAide: String
    val viderCopiesDEnvoi: String
    val signalerUnProbleme: String
    val envoyerLeRapport: String
    val journalIllisible: String
    val rapportExplication: String
    val importExplication: String
    val creditIcones: String
    val creditEmojis: String
    val creditPolice: String
    val utiliserCetteImage: String
    val pincezPourAgrandir: String
    val preparation: String
    val envoiDeLImage: String
    val envoiEchoue: String
    val connexionArrierePlan: String
    val recoitEnArrierePlan: String
    val canalVeilleDescription: String
    val messageChiffreNonLisible: String
    val quelquunMaj: String
    val repondrePoints: String
    val avancementIntrouvable: String
    val aucunServeurPourAnnonces: String
    val aucuneVersionAnnoncee: String
    val creationServeurImpossible: String
    val quitterServeurImpossible: String
    val supprimerLeServeur: String
    val suppressionServeurImpossible: String
    val creationSalonImpossible: String
    val creationGroupeImpossible: String
    val suppressionImpossible: String
    val creationCategorieImpossible: String
    val deplacementImpossible: String
    val decorationNonImportee: String
    val ornementNonPose: String
    val nomNonChange: String
    val relationNonEcrite: String
    val aujourdhui: String
    val appelImpossibleCourt: String
    val messagesPrives: String
    val commencerLaConversation: String
    val plusieursEcrivent: String
    val roleFacultatifAide: String
    val connexionDiscord: String
    val seConnecterADiscord: String
    val passerelleIntro: String
    val connexionDiscordAide: String
    val emojisUtilisesAide: String
    val passerelleAvertissement: String
    val rejoindre: String
    val nom: String
    val inviter: String
    val conversation: String
    val moi: String
    val decoration: String
    val decorations: String
    val changer: String
    val retirer: String
    val dicterCourt: String
    val vocalEnregistrer: String
    val vocalEnvoyer: String
    val transferer: String
    val transfererVers: String
    val transfererSousTitre: String
    val ajouterUnMessageFacultatif: String
    val transfertEchoue: String
    val appel: String
    val appeler: String
    val raccrocher: String
    val fil: String
    val fils: String
    val plus: String
    val modifier: String
    val autocollant: String
    val autocollants: String
    val divers: String
    val effacer: String
    val selectionner: String
    val primaire: String
    val secondaire: String
    val appliquer: String
    val desepingler: String
    val epingles: String
    val epinglee: String
    val evenements: String
    val membres: String
    val liens: String
    val photo: String
    val expiration: String
    val generer: String
    val illimite: String
    val jamais: String
    val usages: String
    val invitations: String
    val invitation: String
    val active: String
    val actif: String
    val aucun: String
    val aucune: String
    val importer: String
    val importCourt: String
    val ornements: String
    val pronoms: String
    val bio: String
    val identite: String
    val confidentialite: String
    val stockage: String
    val notifications: String
    val mentions: String
    val hier: String
    val lire: String
    val pause: String
    val supprimer: String
    val tous: String
    val amis: String
    val boutique: String
    val installer: String
    val couleur: String
    val apparence: String
    val couperCourt: String
    val deconnexion: String
    val micro: String
    val son: String
    val personnalise: String
    val profils: String
    val favoris: String
    val recadrer: String
    val ajouter: String
    val expression: String
    val tag: String
    val suivant: String
    val reinitialiser: String
    val description: String
    val icone: String
    val renvoyer: String
    val salons: String
    val coller: String

    // the sentences with holes
    fun membresCompte(nombre: Int): String
    fun reponsesNombre(nombre: Int): String
    fun messagesNombre(nombre: Int): String
    fun nouveauxMessages(nombre: Int): String
    fun invitationsNombre(nombre: Int): String
    fun votesNombre(nombre: Int): String
    fun salonsNombre(nombre: Int): String
    fun enLigneNombre(nombre: Int): String
    fun objetsNombre(nombre: Int): String
    fun usagesRestants(nombre: Int): String
    fun rolesAvecNombre(nombre: Int): String
    fun creerEtDonnerA(nombre: Int): String
    fun evenementsNombre(nombre: Int): String
    fun arriveesEtDeparts(nombre: Int): String
    fun arriveesNombre(nombre: Int): String
    fun departsNombre(nombre: Int): String
    fun ecrit(qui: String): String
    fun ecrivent(un: String, deux: String): String
    fun chargementEpinglesNombre(nombre: Int): String
    fun remonteeHistorique(nombre: Int): String
    fun envoyerAPersonne(qui: String): String
    fun envoyerDansSalon(salon: String): String
    fun messageA(qui: String): String
    fun messageDans(salon: String): String
    fun echecAvecRaison(raison: String): String
    fun chercherDansSalonNomme(salon: String): String
    fun rechercheExplication(salon: String): String
    fun aucunMessageContenant(terme: String, salon: String): String
    fun aucunMessageContenantCourt(terme: String): String
    fun aucunSalonNomme(terme: String): String
    fun rechercherDansServeur(serveur: String): String
    fun rienPour(terme: String): String
    fun ecranDe(qui: String): String
    fun cestVous(qui: String): String
    fun vousEt(qui: String): String
    fun vousEtAutres(nombre: Int): String
    fun dicteeInjoignableCause(cause: String): String
    fun velvetVersion(version: String): String
    fun versionDisponible(version: String): String
    fun versionDisponibleBandeau(version: String): String
    fun versionPasPourCetAppareil(version: String): String
    fun versionPreteALaFermeture(version: String): String
    fun versionPreteAInstaller(version: String): String
    fun telechargementPourcent(pourcent: Int): String
    fun chercherGifSur(service: String): String
    fun tendancesDe(service: String): String
    fun rechercheEnvoyeeA(service: String): String
    fun importAvancement(faits: Int, total: Int): String
    fun importAvancementDetail(faits: Int, total: Int, evenements: Int): String
    fun importVitesse(vitesse: String): String
    fun importPieces(nombre: Int): String
    fun importTermineEvenements(nombre: Int): String
    fun importInterrompuQuoi(quoi: String): String
    fun importInjoignable(quoi: String): String
    val importRaisonInconnue: String
    fun etaSecondes(secondes: Long): String
    fun etaMinutes(minutes: Long): String
    fun etaHeures(heures: Long, minutes: Long): String
    fun tailleKo(nombre: Long): String
    fun tailleMo(nombre: Long): String
    fun tailleOctets(nombre: Long): String
    fun profilNonEnregistre(quoi: String): String
    fun imageNonEnvoyee(quoi: String): String
    fun avatarNonChange(quoi: String): String
    fun pseudoNonChange(quoi: String): String
    fun iconeNonChangee(quoi: String): String
    fun iconeNonSupprimee(quoi: String): String
    fun nonEnregistreQuoi(quoi: String): String
    fun serveursIndisponibles(quoi: String): String
    fun copiesDEnvoi(taille: String): String
    fun nouveauSalonDans(serveur: String): String
    fun nouvelleCategorieDans(serveur: String): String
    fun creerSalonNomme(nom: String): String
    fun ajouterEmojiNomme(nom: String): String
    fun texteMaxCaracteres(nombre: Int): String
    fun jusquACaracteres(nombre: Int): String
    fun groupeChoisis(nombre: Int): String
    fun inviteDesAmisSur(serveur: String): String
    fun inviterDansServeur(serveur: String): String
    fun depuisDate(date: String): String
    fun appareilDemandeVerification(appareil: String): String
    fun nommerQui(qui: String): String
    fun modifierNomme(nom: String): String
    fun palierOccupe(niveau: Long): String
    fun pouvoirNiveau(niveau: Long): String
    fun appliqueDans(nombre: Int): String
    fun appliqueRefuse(appliques: Int, refuses: Int, raison: String): String

    // the last few
    fun pageNumeroDe(numero: Int): String
    val separateurNouveauxMessages: String
    val nouveau: String
    val uniteOctets: String
    val uniteKilooctets: String
    val uniteMegaoctets: String
    val uniteGigaoctets: String
    fun rapportPret(ko: Long): String
    fun repondreA(qui: String): String
    fun reponseA(qui: String): String
    fun nouveauxMessagesSeparateur(nombre: Int): String
    fun rechercherPersonne(qui: String): String
    fun apercuDe(quoi: String): String
    val tagExemple: String
    fun velvetVersionOu(version: String): String
    val versionDeDeveloppement: String
    val automatique: String
    fun reponseNumero(numero: Int): String
    fun caracteresSur(ecrits: Int, total: Int): String
    fun aucunEmojiPour(recherche: String): String
    fun emojisCorrespondant(fragment: String): String
    fun membresCorrespondant(fragment: String): String
    fun salonsCorrespondant(fragment: String): String
    val pronomsExemple: String
    val roleExemple: String
    fun tailleEnKo(nombre: Long): String

    // creating a channel
    val salonTexte: String
    val salonVocal: String
    val salonTexteDetail: String
    val salonVocalDetail: String
    fun creerSalonVocalNomme(nom: String): String

    // notifications, the general setting
    val notificationsAide: String
    val notifTout: String
    val notifMentions: String
    val notifMuet: String
    val notifToutDetail: String
    val notifMentionsDetail: String
    val notifMuetDetail: String

    val retour: String
    val fermer: String
    val annuler: String
    val valider: String
}

/** the languages offered. the order is the picker's */
enum class Langue(val code: String, val textes: Traduction) {
    FRANCAIS("fr", Francais),
    ANGLAIS("en", English),
    ;

    companion object {
        /** the system's language, or french, which is the language of the house */
        fun depuisCode(code: String?): Langue =
            entries.firstOrNull { it.code == code?.take(2)?.lowercase() } ?: FRANCAIS
    }
}

val LocalLangue = staticCompositionLocalOf { Langue.FRANCAIS }

/**
 * the language outside the composition
 *
 * a notification, a foreground service, a date format: all of it speaks to somebody without ever
 * going through compose, so without `LocalLangue`. each app root copies the chosen language in
 * here, and whatever is not composable reads [horsUi]
 */
var langueCourante: Langue = Langue.FRANCAIS
    set(valeur) {
        field = valeur
        // the engine's own sentences (errors, role names) follow, see `li.velvetchat.core.tr`
        li.velvetchat.core.LangueMoteur.anglais = valeur == Langue.ANGLAIS
    }

/** the current language's texts, for code that is not a composable */
val horsUi: Traduction get() = langueCourante.textes

/** how the language gets changed, supplied by the app, which knows where it keeps its settings */
val LocalChoisirLangue = staticCompositionLocalOf<(Langue) -> Unit> { {} }

/** the current language's texts. `val t = textes()` at the top of a composable */
@Composable
@ReadOnlyComposable
fun textes(): Traduction = LocalLangue.current.textes
