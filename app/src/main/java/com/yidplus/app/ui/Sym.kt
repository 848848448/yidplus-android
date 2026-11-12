package com.yidplus.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Google Material Symbols Rounded (weight 400, grade 0, optical size 24) as ImageVectors.
 * Same property names as the Icons.Filled / Icons.AutoMirrored.Filled names the app used (Sym.Send, Sym.Close ...).
 * Sym.X = FILL 0 (the current Google default look), Sym.XFill = FILL 1 (use for selected / active states).
 * Built on first use (lazy), no network, no font file. Arrows/send/reply auto-mirror in RTL.
 */
object Sym {
val AccountBox by lazy { s(SymP1.accountBox) }
val AccountBoxFill by lazy { s(SymP1.accountBoxF) }
val AccountCircle by lazy { s(SymP1.accountCircle) }
val AccountCircleFill by lazy { s(SymP1.accountCircleF) }
val Add by lazy { s(SymP1.add) }
val AddFill get() = Add
val AddCircle by lazy { s(SymP1.addCircle) }
val AddCircleFill by lazy { s(SymP1.addCircleF) }
val AddReaction by lazy { s(SymP1.addReaction) }
val AddReactionFill by lazy { s(SymP1.addReactionF) }
val AdminPanelSettings by lazy { s(SymP1.adminPanelSettings) }
val AdminPanelSettingsFill by lazy { s(SymP1.adminPanelSettingsF) }
val AlternateEmail by lazy { s(SymP1.alternateEmail) }
val AlternateEmailFill get() = AlternateEmail
val ArrowBack by lazy { s(SymP1.arrowBack, true) }
val ArrowBackFill get() = ArrowBack
val Article by lazy { s(SymP1.article, true) }
val ArticleFill by lazy { s(SymP1.articleF, true) }
val AttachFile by lazy { s(SymP1.attachFile) }
val AttachFileFill get() = AttachFile
val Block by lazy { s(SymP1.block) }
val BlockFill by lazy { s(SymP1.blockF) }
val Bookmark by lazy { s(SymP1.bookmark) }
val BookmarkFill by lazy { s(SymP1.bookmarkF) }
val BookmarkBorder by lazy { s(SymP1.bookmarkBorder) }
val BookmarkBorderFill by lazy { s(SymP1.bookmarkBorderF) }
val BrokenImage by lazy { s(SymP1.brokenImage) }
val BrokenImageFill by lazy { s(SymP1.brokenImageF) }
val Build by lazy { s(SymP1.build) }
val BuildFill by lazy { s(SymP1.buildF) }
val Call by lazy { s(SymP1.call) }
val CallFill by lazy { s(SymP1.callF) }
val CameraAlt by lazy { s(SymP1.cameraAlt) }
val CameraAltFill by lazy { s(SymP1.cameraAltF) }
val Campaign by lazy { s(SymP1.campaign) }
val CampaignFill by lazy { s(SymP1.campaignF) }
val Chat by lazy { s(SymP1.chat, true) }
val ChatFill by lazy { s(SymP1.chatF, true) }
val ChatBubble by lazy { s(SymP1.chatBubble) }
val ChatBubbleFill by lazy { s(SymP1.chatBubbleF) }
val ChatBubbleOutline by lazy { s(SymP1.chatBubbleOutline) }
val ChatBubbleOutlineFill by lazy { s(SymP1.chatBubbleOutlineF) }
val Check by lazy { s(SymP1.check) }
val CheckFill get() = Check
val CheckCircle by lazy { s(SymP1.checkCircle) }
val CheckCircleFill by lazy { s(SymP1.checkCircleF) }
val ChevronRight by lazy { s(SymP1.chevronRight, true) }
val ChevronRightFill get() = ChevronRight
val CleaningServices by lazy { s(SymP1.cleaningServices) }
val CleaningServicesFill by lazy { s(SymP1.cleaningServicesF) }
val Close by lazy { s(SymP1.close) }
val CloseFill get() = Close
val CloudOff by lazy { s(SymP1.cloudOff) }
val CloudOffFill by lazy { s(SymP1.cloudOffF) }
val ConfirmationNumber by lazy { s(SymP1.confirmationNumber) }
val ConfirmationNumberFill by lazy { s(SymP1.confirmationNumberF) }
val ContactPhone by lazy { s(SymP1.contactPhone) }
val ContactPhoneFill by lazy { s(SymP1.contactPhoneF) }
val ContentCopy by lazy { s(SymP1.contentCopy) }
val ContentCopyFill by lazy { s(SymP1.contentCopyF) }
val Contrast by lazy { s(SymP1.contrast) }
val ContrastFill get() = Contrast
val DarkMode by lazy { s(SymP1.darkMode) }
val DarkModeFill by lazy { s(SymP1.darkModeF) }
val Delete by lazy { s(SymP1.delete) }
val DeleteFill by lazy { s(SymP1.deleteF) }
val DeleteForever by lazy { s(SymP1.deleteForever) }
val DeleteForeverFill by lazy { s(SymP1.deleteForeverF) }
val DeleteOutline by lazy { s(SymP1.deleteOutline) }
val DeleteOutlineFill by lazy { s(SymP1.deleteOutlineF) }
val DeleteSweep by lazy { s(SymP2.deleteSweep) }
val DeleteSweepFill by lazy { s(SymP2.deleteSweepF) }
val Description by lazy { s(SymP2.description) }
val DescriptionFill by lazy { s(SymP2.descriptionF) }
val Devices by lazy { s(SymP2.devices) }
val DevicesFill by lazy { s(SymP2.devicesF) }
val Done by lazy { s(SymP2.done) }
val DoneFill get() = Done
val DoneAll by lazy { s(SymP2.doneAll) }
val DoneAllFill get() = DoneAll
val Download by lazy { s(SymP2.download) }
val DownloadFill get() = Download
val Edit by lazy { s(SymP2.edit) }
val EditFill by lazy { s(SymP2.editF) }
val EmojiEmotions by lazy { s(SymP2.emojiEmotions) }
val EmojiEmotionsFill by lazy { s(SymP2.emojiEmotionsF) }
val ErrorOutline by lazy { s(SymP2.errorOutline) }
val ErrorOutlineFill by lazy { s(SymP2.errorOutlineF) }
val ExitToApp by lazy { s(SymP2.exitToApp, true) }
val ExitToAppFill get() = ExitToApp
val Explore by lazy { s(SymP2.explore) }
val ExploreFill by lazy { s(SymP2.exploreF) }
val Favorite by lazy { s(SymP2.favorite) }
val FavoriteFill by lazy { s(SymP2.favoriteF) }
val FavoriteBorder by lazy { s(SymP2.favoriteBorder) }
val FavoriteBorderFill by lazy { s(SymP2.favoriteBorderF) }
val Feedback by lazy { s(SymP2.feedback) }
val FeedbackFill by lazy { s(SymP2.feedbackF) }
val Flag by lazy { s(SymP2.flag) }
val FlagFill by lazy { s(SymP2.flagF) }
val FormatSize by lazy { s(SymP2.formatSize) }
val FormatSizeFill get() = FormatSize
val Forum by lazy { s(SymP2.forum) }
val ForumFill by lazy { s(SymP2.forumF) }
val Forward by lazy { s(SymP2.forward, true) }
val ForwardFill get() = Forward
val Forward10 by lazy { s(SymP2.forward10) }
val Forward10Fill get() = Forward10
val Group by lazy { s(SymP2.group) }
val GroupFill by lazy { s(SymP2.groupF) }
val Headphones by lazy { s(SymP2.headphones) }
val HeadphonesFill by lazy { s(SymP2.headphonesF) }
val HelpOutline by lazy { s(SymP2.helpOutline, true) }
val HelpOutlineFill by lazy { s(SymP2.helpOutlineF, true) }
val History by lazy { s(SymP2.history) }
val HistoryFill get() = History
val HourglassTop by lazy { s(SymP2.hourglassTop) }
val HourglassTopFill get() = HourglassTop
val Image by lazy { s(SymP2.image) }
val ImageFill by lazy { s(SymP2.imageF) }
val Info by lazy { s(SymP2.info) }
val InfoFill by lazy { s(SymP2.infoF) }
val Insights by lazy { s(SymP2.insights) }
val InsightsFill get() = Insights
val Keyboard by lazy { s(SymP2.keyboard) }
val KeyboardFill by lazy { s(SymP2.keyboardF) }
val KeyboardArrowDown by lazy { s(SymP2.keyboardArrowDown) }
val KeyboardArrowDownFill get() = KeyboardArrowDown
val KeyboardArrowUp by lazy { s(SymP2.keyboardArrowUp) }
val KeyboardArrowUpFill get() = KeyboardArrowUp
val Link by lazy { s(SymP2.link) }
val LinkFill get() = Link
val Lock by lazy { s(SymP2.lock) }
val LockFill by lazy { s(SymP2.lockF) }
val Login by lazy { s(SymP2.login, true) }
val LoginFill get() = Login
val Logout by lazy { s(SymP2.logout, true) }
val LogoutFill get() = Logout
val Mail by lazy { s(SymP2.mail) }
val MailFill by lazy { s(SymP2.mailF) }
val Mic by lazy { s(SymP2.mic) }
val MicFill by lazy { s(SymP2.micF) }
val MicOff by lazy { s(SymP3.micOff) }
val MicOffFill by lazy { s(SymP3.micOffF) }
val MilitaryTech by lazy { s(SymP3.militaryTech) }
val MilitaryTechFill by lazy { s(SymP3.militaryTechF) }
val MoreVert by lazy { s(SymP3.moreVert) }
val MoreVertFill get() = MoreVert
val MotionPhotosOff by lazy { s(SymP3.motionPhotosOff) }
val MotionPhotosOffFill by lazy { s(SymP3.motionPhotosOffF) }
val Movie by lazy { s(SymP3.movie) }
val MovieFill by lazy { s(SymP3.movieF) }
val MusicNote by lazy { s(SymP3.musicNote) }
val MusicNoteFill get() = MusicNote
val Notifications by lazy { s(SymP3.notifications) }
val NotificationsFill by lazy { s(SymP3.notificationsF) }
val NotificationsActive by lazy { s(SymP3.notificationsActive) }
val NotificationsActiveFill by lazy { s(SymP3.notificationsActiveF) }
val NotificationsNone by lazy { s(SymP3.notificationsNone) }
val NotificationsNoneFill by lazy { s(SymP3.notificationsNoneF) }
val NotificationsOff by lazy { s(SymP3.notificationsOff) }
val NotificationsOffFill by lazy { s(SymP3.notificationsOffF) }
val OpenInNew by lazy { s(SymP3.openInNew, true) }
val OpenInNewFill get() = OpenInNew
val Palette by lazy { s(SymP3.palette) }
val PaletteFill by lazy { s(SymP3.paletteF) }
val Password by lazy { s(SymP3.password) }
val PasswordFill get() = Password
val Pause by lazy { s(SymP3.pause) }
val PauseFill by lazy { s(SymP3.pauseF) }
val People by lazy { s(SymP3.people) }
val PeopleFill by lazy { s(SymP3.peopleF) }
val Person by lazy { s(SymP3.person) }
val PersonFill by lazy { s(SymP3.personF) }
val PersonAdd by lazy { s(SymP3.personAdd) }
val PersonAddFill by lazy { s(SymP3.personAddF) }
val PersonRemove by lazy { s(SymP3.personRemove) }
val PersonRemoveFill by lazy { s(SymP3.personRemoveF) }
val PersonSearch by lazy { s(SymP3.personSearch) }
val PersonSearchFill by lazy { s(SymP3.personSearchF) }
val PhoneDisabled by lazy { s(SymP3.phoneDisabled) }
val PhoneDisabledFill by lazy { s(SymP3.phoneDisabledF) }
val PhoneMissed by lazy { s(SymP3.phoneMissed) }
val PhoneMissedFill by lazy { s(SymP3.phoneMissedF) }
val PhotoCamera by lazy { s(SymP3.photoCamera) }
val PhotoCameraFill by lazy { s(SymP3.photoCameraF) }
val PhotoLibrary by lazy { s(SymP3.photoLibrary) }
val PhotoLibraryFill by lazy { s(SymP3.photoLibraryF) }
val PlayArrow by lazy { s(SymP3.playArrow) }
val PlayArrowFill by lazy { s(SymP3.playArrowF) }
val PlayCircle by lazy { s(SymP3.playCircle) }
val PlayCircleFill by lazy { s(SymP3.playCircleF) }
val Podcasts by lazy { s(SymP3.podcasts) }
val PodcastsFill get() = Podcasts
val Poll by lazy { s(SymP3.poll) }
val PollFill by lazy { s(SymP3.pollF) }
val PrivacyTip by lazy { s(SymP3.privacyTip) }
val PrivacyTipFill by lazy { s(SymP3.privacyTipF) }
val Public by lazy { s(SymP3.public) }
val PublicFill get() = Public
val PushPin by lazy { s(SymP3.pushPin) }
val PushPinFill by lazy { s(SymP3.pushPinF) }
val RadioButtonChecked by lazy { s(SymP3.radioButtonChecked) }
val RadioButtonCheckedFill get() = RadioButtonChecked
val RadioButtonUnchecked by lazy { s(SymP3.radioButtonUnchecked) }
val RadioButtonUncheckedFill get() = RadioButtonUnchecked
val Refresh by lazy { s(SymP3.refresh) }
val RefreshFill get() = Refresh
val Replay10 by lazy { s(SymP4.replay10) }
val Replay10Fill get() = Replay10
val Reply by lazy { s(SymP4.reply, true) }
val ReplyFill get() = Reply
val Search by lazy { s(SymP4.search) }
val SearchFill get() = Search
val SearchOff by lazy { s(SymP4.searchOff) }
val SearchOffFill get() = SearchOff
val Security by lazy { s(SymP4.security) }
val SecurityFill get() = Security
val Sell by lazy { s(SymP4.sell) }
val SellFill by lazy { s(SymP4.sellF) }
val Send by lazy { s(SymP4.send, true) }
val SendFill by lazy { s(SymP4.sendF, true) }
val Settings by lazy { s(SymP4.settings) }
val SettingsFill by lazy { s(SymP4.settingsF) }
val Share by lazy { s(SymP4.share) }
val ShareFill by lazy { s(SymP4.shareF) }
val Shield by lazy { s(SymP4.shield) }
val ShieldFill by lazy { s(SymP4.shieldF) }
val SkipNext by lazy { s(SymP4.skipNext) }
val SkipNextFill by lazy { s(SymP4.skipNextF) }
val SkipPrevious by lazy { s(SymP4.skipPrevious) }
val SkipPreviousFill by lazy { s(SymP4.skipPreviousF) }
val Star by lazy { s(SymP4.star) }
val StarFill by lazy { s(SymP4.starF) }
val StarBorder by lazy { s(SymP4.starBorder) }
val StarBorderFill by lazy { s(SymP4.starBorderF) }
val SupportAgent by lazy { s(SymP4.supportAgent) }
val SupportAgentFill get() = SupportAgent
val SystemUpdate by lazy { s(SymP4.systemUpdate) }
val SystemUpdateFill by lazy { s(SymP4.systemUpdateF) }
val TravelExplore by lazy { s(SymP4.travelExplore) }
val TravelExploreFill get() = TravelExplore
val Tune by lazy { s(SymP4.tune) }
val TuneFill get() = Tune
val Upload by lazy { s(SymP4.upload) }
val UploadFill get() = Upload
val Verified by lazy { s(SymP4.verified) }
val VerifiedFill by lazy { s(SymP4.verifiedF) }
val VideoLibrary by lazy { s(SymP4.videoLibrary) }
val VideoLibraryFill by lazy { s(SymP4.videoLibraryF) }
val Videocam by lazy { s(SymP4.videocam) }
val VideocamFill by lazy { s(SymP4.videocamF) }
val Visibility by lazy { s(SymP4.visibility) }
val VisibilityFill by lazy { s(SymP4.visibilityF) }
val VisibilityOff by lazy { s(SymP4.visibilityOff) }
val VisibilityOffFill by lazy { s(SymP4.visibilityOffF) }
val VolumeOff by lazy { s(SymP4.volumeOff, true) }
val VolumeOffFill by lazy { s(SymP4.volumeOffF, true) }
val VolumeUp by lazy { s(SymP4.volumeUp, true) }
val VolumeUpFill by lazy { s(SymP4.volumeUpF, true) }
val WavingHand by lazy { s(SymP4.wavingHand) }
val WavingHandFill by lazy { s(SymP4.wavingHandF) }
val Archive by lazy { s(SymP4.archive) }
val ArchiveFill by lazy { s(SymP4.archiveF) }
val Unarchive by lazy { s(SymP4.unarchive) }
val UnarchiveFill by lazy { s(SymP4.unarchiveF) }
val MarkChatUnread by lazy { s(SymP4.markChatUnread) }
val MarkChatUnreadFill by lazy { s(SymP4.markChatUnreadF) }
val MarkChatRead by lazy { s(SymP4.markChatRead) }
val MarkChatReadFill by lazy { s(SymP4.markChatReadF) }
val FilterList by lazy { s(SymP4.filterList) }
val FilterListFill get() = FilterList
val Sort by lazy { s(SymP4.sort, true) }
val SortFill get() = Sort
val Tag by lazy { s(SymP4.tag) }
val TagFill get() = Tag
val Mood by lazy { s(SymP5.mood) }
val MoodFill by lazy { s(SymP5.moodF) }
val Gif by lazy { s(SymP5.gif) }
val GifFill get() = Gif
val StickyNote2 by lazy { s(SymP5.stickyNote2) }
val StickyNote2Fill by lazy { s(SymP5.stickyNote2F) }
val KeyboardVoice by lazy { s(SymP5.keyboardVoice) }
val KeyboardVoiceFill by lazy { s(SymP5.keyboardVoiceF) }
val LocationOn by lazy { s(SymP5.locationOn) }
val LocationOnFill by lazy { s(SymP5.locationOnF) }
val Contacts by lazy { s(SymP5.contacts) }
val ContactsFill by lazy { s(SymP5.contactsF) }
val Timer by lazy { s(SymP5.timer) }
val TimerFill by lazy { s(SymP5.timerF) }
val Translate by lazy { s(SymP5.translate) }
val TranslateFill get() = Translate
val Schedule by lazy { s(SymP5.schedule) }
val ScheduleFill by lazy { s(SymP5.scheduleF) }
val ScheduleSend by lazy { s(SymP5.scheduleSend, true) }
val ScheduleSendFill by lazy { s(SymP5.scheduleSendF, true) }
val Wallpaper by lazy { s(SymP5.wallpaper) }
val WallpaperFill get() = Wallpaper
val Language by lazy { s(SymP5.language) }
val LanguageFill get() = Language
val Storage by lazy { s(SymP5.storage) }
val StorageFill get() = Storage
val DataUsage by lazy { s(SymP5.dataUsage) }
val DataUsageFill get() = DataUsage
val Help by lazy { s(SymP5.help, true) }
val HelpFill by lazy { s(SymP5.helpF, true) }
val QrCode by lazy { s(SymP5.qrCode) }
val QrCodeFill get() = QrCode
val QrCodeScanner by lazy { s(SymP5.qrCodeScanner) }
val QrCodeScannerFill get() = QrCodeScanner
val CallEnd by lazy { s(SymP5.callEnd) }
val CallEndFill by lazy { s(SymP5.callEndF) }
val CallMade by lazy { s(SymP5.callMade) }
val CallMadeFill get() = CallMade
val CallReceived by lazy { s(SymP5.callReceived) }
val CallReceivedFill get() = CallReceived
val VideocamOff by lazy { s(SymP5.videocamOff) }
val VideocamOffFill by lazy { s(SymP5.videocamOffF) }
val VolumeDown by lazy { s(SymP5.volumeDown, true) }
val VolumeDownFill by lazy { s(SymP5.volumeDownF, true) }
val ReplyAll by lazy { s(SymP5.replyAll, true) }
val ReplyAllFill get() = ReplyAll
val SelectAll by lazy { s(SymP5.selectAll) }
val SelectAllFill get() = SelectAll
val Undo by lazy { s(SymP5.undo, true) }
val UndoFill get() = Undo
val Redo by lazy { s(SymP5.redo, true) }
val RedoFill get() = Redo
val Menu by lazy { s(SymP5.menu) }
val MenuFill get() = Menu
val Home by lazy { s(SymP5.home) }
val HomeFill by lazy { s(SymP5.homeF) }
val Groups by lazy { s(SymP5.groups) }
val GroupsFill by lazy { s(SymP5.groupsF) }
val Photo by lazy { s(SymP5.photo) }
val PhotoFill by lazy { s(SymP5.photoF) }
val DownloadDone by lazy { s(SymP5.downloadDone) }
val DownloadDoneFill get() = DownloadDone
val CloudDownload by lazy { s(SymP5.cloudDownload) }
val CloudDownloadFill by lazy { s(SymP5.cloudDownloadF) }
val CloudUpload by lazy { s(SymP5.cloudUpload) }
val CloudUploadFill by lazy { s(SymP5.cloudUploadF) }
val Sync by lazy { s(SymP5.sync) }
val SyncFill get() = Sync
val Wifi by lazy { s(SymP5.wifi) }
val WifiFill get() = Wifi
val WifiOff by lazy { s(SymP6.wifiOff) }
val WifiOffFill get() = WifiOff
val SignalCellularAlt by lazy { s(SymP6.signalCellularAlt) }
val SignalCellularAltFill get() = SignalCellularAlt
val BatteryFull by lazy { s(SymP6.batteryFull) }
val BatteryFullFill get() = BatteryFull
val Key by lazy { s(SymP6.key) }
val KeyFill by lazy { s(SymP6.keyF) }
val Fingerprint by lazy { s(SymP6.fingerprint) }
val FingerprintFill get() = Fingerprint
val Badge by lazy { s(SymP6.badge) }
val BadgeFill by lazy { s(SymP6.badgeF) }
val Phone by lazy { s(SymP6.phone) }
val PhoneFill by lazy { s(SymP6.phoneF) }
val Sms by lazy { s(SymP6.sms) }
val SmsFill by lazy { s(SymP6.smsF) }
val Inbox by lazy { s(SymP6.inbox) }
val InboxFill by lazy { s(SymP6.inboxF) }
val Drafts by lazy { s(SymP6.drafts) }
val DraftsFill by lazy { s(SymP6.draftsF) }
val Label by lazy { s(SymP6.label, true) }
val LabelFill by lazy { s(SymP6.labelF, true) }
val ExpandMore by lazy { s(SymP6.expandMore) }
val ExpandMoreFill get() = ExpandMore
val ExpandLess by lazy { s(SymP6.expandLess) }
val ExpandLessFill get() = ExpandLess
val ChevronLeft by lazy { s(SymP6.chevronLeft, true) }
val ChevronLeftFill get() = ChevronLeft
val ArrowForward by lazy { s(SymP6.arrowForward, true) }
val ArrowForwardFill get() = ArrowForward
val ArrowUpward by lazy { s(SymP6.arrowUpward) }
val ArrowUpwardFill get() = ArrowUpward
val ArrowDownward by lazy { s(SymP6.arrowDownward) }
val ArrowDownwardFill get() = ArrowDownward
val KeyboardArrowLeft by lazy { s(SymP6.keyboardArrowLeft, true) }
val KeyboardArrowLeftFill get() = KeyboardArrowLeft
val KeyboardArrowRight by lazy { s(SymP6.keyboardArrowRight, true) }
val KeyboardArrowRightFill get() = KeyboardArrowRight
val KeyboardBackspace by lazy { s(SymP6.keyboardBackspace, true) }
val KeyboardBackspaceFill get() = KeyboardBackspace
val MoreHoriz by lazy { s(SymP6.moreHoriz) }
val MoreHorizFill get() = MoreHoriz
val Remove by lazy { s(SymP6.remove) }
val RemoveFill get() = Remove
val RemoveCircle by lazy { s(SymP6.removeCircle) }
val RemoveCircleFill by lazy { s(SymP6.removeCircleF) }
val Cancel by lazy { s(SymP6.cancel) }
val CancelFill by lazy { s(SymP6.cancelF) }
val Error by lazy { s(SymP6.error) }
val ErrorFill by lazy { s(SymP6.errorF) }
val Warning by lazy { s(SymP6.warning) }
val WarningFill by lazy { s(SymP6.warningF) }
val HelpCenter by lazy { s(SymP6.helpCenter) }
val HelpCenterFill by lazy { s(SymP6.helpCenterF) }
val Lightbulb by lazy { s(SymP6.lightbulb) }
val LightbulbFill by lazy { s(SymP6.lightbulbF) }
val LightMode by lazy { s(SymP6.lightMode) }
val LightModeFill by lazy { s(SymP6.lightModeF) }
val BrightnessAuto by lazy { s(SymP6.brightnessAuto) }
val BrightnessAutoFill by lazy { s(SymP6.brightnessAutoF) }
val TextFields by lazy { s(SymP6.textFields) }
val TextFieldsFill get() = TextFields
val FormatBold by lazy { s(SymP6.formatBold) }
val FormatBoldFill get() = FormatBold
val FormatItalic by lazy { s(SymP6.formatItalic) }
val FormatItalicFill get() = FormatItalic
val StrikethroughS by lazy { s(SymP6.strikethroughS) }
val StrikethroughSFill get() = StrikethroughS
val Code by lazy { s(SymP6.code) }
val CodeFill get() = Code
val FormatQuote by lazy { s(SymP6.formatQuote) }
val FormatQuoteFill by lazy { s(SymP6.formatQuoteF) }
val Accessibility by lazy { s(SymP6.accessibility) }
val AccessibilityFill get() = Accessibility
val Animation by lazy { s(SymP6.animation) }
val AnimationFill by lazy { s(SymP6.animationF) }
val BlurOn by lazy { s(SymP6.blurOn) }
val BlurOnFill get() = BlurOn
val Celebration by lazy { s(SymP7.celebration) }
val CelebrationFill by lazy { s(SymP7.celebrationF) }
val Pin by lazy { s(SymP7.pin) }
val PinFill by lazy { s(SymP7.pinF) }
val PersonOff by lazy { s(SymP7.personOff) }
val PersonOffFill by lazy { s(SymP7.personOffF) }
val PersonAddDisabled by lazy { s(SymP7.personAddDisabled) }
val PersonAddDisabledFill by lazy { s(SymP7.personAddDisabledF) }
val GroupAdd by lazy { s(SymP7.groupAdd) }
val GroupAddFill by lazy { s(SymP7.groupAddF) }
val GroupRemove by lazy { s(SymP7.groupRemove) }
val GroupRemoveFill by lazy { s(SymP7.groupRemoveF) }
val ManageAccounts by lazy { s(SymP7.manageAccounts) }
val ManageAccountsFill by lazy { s(SymP7.manageAccountsF) }
val SupervisorAccount by lazy { s(SymP7.supervisorAccount) }
val SupervisorAccountFill by lazy { s(SymP7.supervisorAccountF) }
val Policy by lazy { s(SymP7.policy) }
val PolicyFill by lazy { s(SymP7.policyF) }
val Gavel by lazy { s(SymP7.gavel) }
val GavelFill get() = Gavel
val Report by lazy { s(SymP7.report) }
val ReportFill by lazy { s(SymP7.reportF) }
val Bolt by lazy { s(SymP7.bolt) }
val BoltFill by lazy { s(SymP7.boltF) }
val Whatshot by lazy { s(SymP7.whatshot) }
val WhatshotFill by lazy { s(SymP7.whatshotF) }
val TrendingUp by lazy { s(SymP7.trendingUp, true) }
val TrendingUpFill get() = TrendingUp
val Leaderboard by lazy { s(SymP7.leaderboard) }
val LeaderboardFill by lazy { s(SymP7.leaderboardF) }
val Bookmarks by lazy { s(SymP7.bookmarks) }
val BookmarksFill by lazy { s(SymP7.bookmarksF) }
val Collections by lazy { s(SymP7.collections) }
val CollectionsFill by lazy { s(SymP7.collectionsF) }
val Folder by lazy { s(SymP7.folder) }
val FolderFill by lazy { s(SymP7.folderF) }
val FolderOpen by lazy { s(SymP7.folderOpen) }
val FolderOpenFill by lazy { s(SymP7.folderOpenF) }
val InsertDriveFile by lazy { s(SymP7.insertDriveFile, true) }
val InsertDriveFileFill by lazy { s(SymP7.insertDriveFileF, true) }
val PictureAsPdf by lazy { s(SymP7.pictureAsPdf) }
val PictureAsPdfFill by lazy { s(SymP7.pictureAsPdfF) }
val AudioFile by lazy { s(SymP7.audioFile) }
val AudioFileFill by lazy { s(SymP7.audioFileF) }
val VideoFile by lazy { s(SymP7.videoFile) }
val VideoFileFill by lazy { s(SymP7.videoFileF) }
val MicNone by lazy { s(SymP7.micNone) }
val MicNoneFill by lazy { s(SymP7.micNoneF) }
val Stop by lazy { s(SymP7.stop) }
val StopFill by lazy { s(SymP7.stopF) }
val Shuffle by lazy { s(SymP7.shuffle) }
val ShuffleFill get() = Shuffle
val Repeat by lazy { s(SymP7.repeat) }
val RepeatFill get() = Repeat
val RepeatOne by lazy { s(SymP7.repeatOne) }
val RepeatOneFill get() = RepeatOne
val QueueMusic by lazy { s(SymP7.queueMusic, true) }
val QueueMusicFill get() = QueueMusic
val LibraryMusic by lazy { s(SymP8.libraryMusic) }
val LibraryMusicFill by lazy { s(SymP8.libraryMusicF) }
val Fullscreen by lazy { s(SymP8.fullscreen) }
val FullscreenFill get() = Fullscreen
val FullscreenExit by lazy { s(SymP8.fullscreenExit) }
val FullscreenExitFill get() = FullscreenExit
val ZoomIn by lazy { s(SymP8.zoomIn) }
val ZoomInFill get() = ZoomIn
val ZoomOut by lazy { s(SymP8.zoomOut) }
val ZoomOutFill get() = ZoomOut
val RotateRight by lazy { s(SymP8.rotateRight) }
val RotateRightFill get() = RotateRight
val Crop by lazy { s(SymP8.crop) }
val CropFill get() = Crop
val Brush by lazy { s(SymP8.brush) }
val BrushFill by lazy { s(SymP8.brushF) }
val Filter by lazy { s(SymP8.filter) }
val FilterFill by lazy { s(SymP8.filterF) }
val ViewList by lazy { s(SymP8.viewList) }
val ViewListFill by lazy { s(SymP8.viewListF) }
val GridView by lazy { s(SymP8.gridView) }
val GridViewFill by lazy { s(SymP8.gridViewF) }
val Draw by lazy { s(SymP8.draw) }
val DrawFill by lazy { s(SymP8.drawF) }
val AddPhotoAlternate by lazy { s(SymP8.addPhotoAlternate) }
val AddPhotoAlternateFill by lazy { s(SymP8.addPhotoAlternateF) }
val AddLink by lazy { s(SymP8.addLink) }
val AddLinkFill get() = AddLink
val LinkOff by lazy { s(SymP8.linkOff) }
val LinkOffFill get() = LinkOff
val Feed by lazy { s(SymP8.feed) }
val FeedFill by lazy { s(SymP8.feedF) }
val RssFeed by lazy { s(SymP8.rssFeed) }
val RssFeedFill get() = RssFeed
val Newspaper by lazy { s(SymP8.newspaper) }
val NewspaperFill get() = Newspaper
val Event by lazy { s(SymP8.event) }
val EventFill by lazy { s(SymP8.eventF) }
val CalendarMonth by lazy { s(SymP8.calendarMonth) }
val CalendarMonthFill by lazy { s(SymP8.calendarMonthF) }
val AccessTime by lazy { s(SymP8.accessTime) }
val AccessTimeFill by lazy { s(SymP8.accessTimeF) }
val Today by lazy { s(SymP8.today) }
val TodayFill by lazy { s(SymP8.todayF) }
val Alarm by lazy { s(SymP8.alarm) }
val AlarmFill by lazy { s(SymP8.alarmF) }
val Snooze by lazy { s(SymP8.snooze) }
val SnoozeFill by lazy { s(SymP8.snoozeF) }
val VolumeMute by lazy { s(SymP8.volumeMute, true) }
val VolumeMuteFill by lazy { s(SymP8.volumeMuteF, true) }
val NotificationImportant by lazy { s(SymP8.notificationImportant) }
val NotificationImportantFill by lazy { s(SymP8.notificationImportantF) }
val Vibration by lazy { s(SymP8.vibration) }
val VibrationFill by lazy { s(SymP8.vibrationF) }
val DoNotDisturbOn by lazy { s(SymP8.doNotDisturbOn) }
val DoNotDisturbOnFill by lazy { s(SymP8.doNotDisturbOnF) }
val MarkEmailRead by lazy { s(SymP8.markEmailRead) }
val MarkEmailReadFill by lazy { s(SymP8.markEmailReadF) }
val ScreenShare by lazy { s(SymP8.screenShare) }
val ScreenShareFill by lazy { s(SymP8.screenShareF) }
val Dialpad by lazy { s(SymP9.dialpad) }
val DialpadFill get() = Dialpad
val PhoneInTalk by lazy { s(SymP9.phoneInTalk) }
val PhoneInTalkFill by lazy { s(SymP9.phoneInTalkF) }
val PhoneCallback by lazy { s(SymP9.phoneCallback) }
val PhoneCallbackFill by lazy { s(SymP9.phoneCallbackF) }
val AddCall by lazy { s(SymP9.addCall) }
val AddCallFill by lazy { s(SymP9.addCallF) }
val Speaker by lazy { s(SymP9.speaker) }
val SpeakerFill by lazy { s(SymP9.speakerF) }
val Bluetooth by lazy { s(SymP9.bluetooth) }
val BluetoothFill get() = Bluetooth
val Hearing by lazy { s(SymP9.hearing) }
val HearingFill get() = Hearing
val Cast by lazy { s(SymP9.cast) }
val CastFill by lazy { s(SymP9.castF) }
val LockOpen by lazy { s(SymP9.lockOpen) }
val LockOpenFill by lazy { s(SymP9.lockOpenF) }
val NoAccounts by lazy { s(SymP9.noAccounts) }
val NoAccountsFill by lazy { s(SymP9.noAccountsF) }
val PersonPin by lazy { s(SymP9.personPin) }
val PersonPinFill by lazy { s(SymP9.personPinF) }
val Dns by lazy { s(SymP9.dns) }
val DnsFill by lazy { s(SymP9.dnsF) }
val Restore by lazy { s(SymP9.restore) }
val RestoreFill get() = Restore
val SettingsBackupRestore by lazy { s(SymP9.settingsBackupRestore) }
val SettingsBackupRestoreFill get() = SettingsBackupRestore
val Backup by lazy { s(SymP9.backup) }
val BackupFill by lazy { s(SymP9.backupF) }
val CheckBox by lazy { s(SymP9.checkBox) }
val CheckBoxFill by lazy { s(SymP9.checkBoxF) }
val CheckBoxOutlineBlank by lazy { s(SymP9.checkBoxOutlineBlank) }
val CheckBoxOutlineBlankFill get() = CheckBoxOutlineBlank
val IndeterminateCheckBox by lazy { s(SymP9.indeterminateCheckBox) }
val IndeterminateCheckBoxFill by lazy { s(SymP9.indeterminateCheckBoxF) }
val ToggleOn by lazy { s(SymP9.toggleOn) }
val ToggleOnFill by lazy { s(SymP9.toggleOnF) }
val ToggleOff by lazy { s(SymP9.toggleOff) }
val ToggleOffFill by lazy { s(SymP9.toggleOffF) }
val Circle by lazy { s(SymP9.circle) }
val CircleFill by lazy { s(SymP9.circleF) }
val EditNote by lazy { s(SymP9.editNote) }
val EditNoteFill by lazy { s(SymP9.editNoteF) }
val Save by lazy { s(SymP9.save) }
val SaveFill by lazy { s(SymP9.saveF) }
val Attachment by lazy { s(SymP9.attachment) }
val AttachmentFill get() = Attachment
val AddComment by lazy { s(SymP9.addComment) }
val AddCommentFill by lazy { s(SymP9.addCommentF) }
val Comment by lazy { s(SymP9.comment, true) }
val CommentFill by lazy { s(SymP9.commentF, true) }
val ModeComment by lazy { s(SymP9.modeComment) }
val ModeCommentFill by lazy { s(SymP9.modeCommentF) }
val QuestionAnswer by lazy { s(SymP9.questionAnswer) }
val QuestionAnswerFill by lazy { s(SymP9.questionAnswerF) }
val Textsms by lazy { s(SymP9.textsms) }
val TextsmsFill by lazy { s(SymP9.textsmsF) }
val ThumbUp by lazy { s(SymP9.thumbUp) }
val ThumbUpFill by lazy { s(SymP9.thumbUpF) }
val ThumbDown by lazy { s(SymP9.thumbDown) }
val ThumbDownFill by lazy { s(SymP9.thumbDownF) }
val OutlinedFlag by lazy { s(SymP9.outlinedFlag) }
val OutlinedFlagFill by lazy { s(SymP9.outlinedFlagF) }
val SentimentSatisfied by lazy { s(SymP9.sentimentSatisfied) }
val SentimentSatisfiedFill by lazy { s(SymP9.sentimentSatisfiedF) }
val EmojiPeople by lazy { s(SymP9.emojiPeople) }
val EmojiPeopleFill get() = EmojiPeople
val HowToReg by lazy { s(SymP10.howToReg) }
val HowToRegFill by lazy { s(SymP10.howToRegF) }
val VerifiedUser by lazy { s(SymP10.verifiedUser) }
val VerifiedUserFill by lazy { s(SymP10.verifiedUserF) }
val NewReleases by lazy { s(SymP10.newReleases) }
val NewReleasesFill by lazy { s(SymP10.newReleasesF) }
val Diamond by lazy { s(SymP10.diamond) }
val DiamondFill by lazy { s(SymP10.diamondF) }
val WorkspacePremium by lazy { s(SymP10.workspacePremium) }
val WorkspacePremiumFill by lazy { s(SymP10.workspacePremiumF) }
}

private fun s(d: String, mirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = "Sym",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
        autoMirror = mirror
    )
        .addGroup(translationY = 960f)
        .addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()
