package dev.romtweaker.app.xp

import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule

/** Diagnóstico de la tanda 2: registra los ids/clases de vistas de SystemUI y qué clases usadas por Iconify existen en esta ROM. */
object IcxScan {
    private const val TAG = "RomTweaker"
    private val seen = HashSet<String>()
    private val buf = StringBuilder()
    private var n = 0
    private var total = 0
    private const val CAP = 4000

    private val classes = listOf(
        "s.ambient.touch.scrim.ScrimManager",
        "s.animation.Expandable",
        "s.animation.view.LaunchableFAB",
        "s.animation.view.LaunchableFrameLayout",
        "s.animation.view.LaunchableImageView",
        "s.animation.view.LaunchableLinearLayout",
        "s.biometrics.AuthController",
        "s.brightness.ui.compose.BrightnessSliderKt",
        "s.brightness.ui.compose.ContainerColors",
        "s.brightness.ui.viewmodel.BrightnessSliderViewModel",
        "s.clock.ui.composable.ClockKt",
        "s.doze.DozeService",
        "s.doze.DozeTriggers",
        "s.doze.DozeUi",
        "s.dreams.touch.scrim.ScrimManager",
        "s.keyguard.KeyguardSliceProvider",
        "s.keyguard.data.repository.KeyguardTransitionRepositoryImpl",
        "s.keyguard.domain.interactor.KeyguardQuickAffordanceInteractor",
        "s.keyguard.ui.binder.KeyguardClockViewBinder",
        "s.keyguard.ui.composable.LockscreenBehindScrimKt",
        "s.keyguard.ui.composable.LockscreenContent",
        "s.keyguard.ui.composable.elements.LockscreenElementFactoryImpl",
        "s.keyguard.ui.composable.elements.StatusBarElementProviderKt",
        "s.keyguard.ui.view.DeviceEntryIconView",
        "s.keyguard.ui.view.layout.sections.AodBurnInLayer",
        "s.keyguard.ui.view.layout.sections.AodBurnInSection",
        "s.keyguard.ui.view.layout.sections.AodNotificationIconsSection",
        "s.keyguard.ui.view.layout.sections.DefaultNotificationStackScrollLayoutSection",
        "s.keyguard.ui.view.layout.sections.SmartspaceSection",
        "s.keyguard.ui.viewmodel.LockscreenBehindScrimViewModel",
        "s.lifecycle.SysUiViewModelKt",
        "s.media.MediaControlPanel",
        "s.media.controls.domain.pipeline.MediaDataManager",
        "s.media.controls.domain.pipeline.MediaDeviceManager",
        "s.media.controls.pipeline.MediaDataManager",
        "s.media.controls.pipeline.MediaDeviceManager",
        "s.media.controls.ui.MediaControlPanel",
        "s.media.controls.ui.MediaViewController",
        "s.media.controls.ui.controller.MediaControlPanel",
        "s.media.controls.ui.controller.MediaViewController",
        "s.media.remedia.ui.compose.MediaKt",
        "s.media.remedia.ui.compose.MediaPresentationStyle",
        "s.monet.ColorScheme",
        "s.monet.Style",
        "s.notifications.stack.emptyshade.ui.composable.EmptyShadeContentImplKt",
        "s.notifications.ui.composable.NotificationsKt",
        "s.plugins.DarkIconDispatcher",
        "s.qs.QSContainerImpl",
        "s.qs.QSFragment",
        "s.qs.QSFragmentLegacy",
        "s.qs.QSImpl",
        "s.qs.QSSecurityFooterUtils",
        "s.qs.QuickStatusBarHeader",
        "s.qs.ax.ui.header.AxQuickSettingsHeaderKt",
        "s.qs.composefragment.QSFragmentCompose",
        "s.qs.composefragment.QSFragmentComposeKt",
        "s.qs.footer.ui.binder.FooterActionsViewBinder",
        "s.qs.footer.ui.compose.ButtonColors",
        "s.qs.footer.ui.compose.FooterActionsDefaults",
        "s.qs.footer.ui.compose.FooterActionsKt",
        "s.qs.footer.ui.viewmodel.FooterActionsButtonViewModel",
        "s.qs.footer.ui.viewmodel.FooterActionsViewModel",
        "s.qs.panels.data.repository.QSColumnsRepository",
        "s.qs.panels.ui.compose.PaginatedGridLayout",
        "s.qs.panels.ui.compose.infinitegrid.CommonTileDefaults",
        "s.qs.panels.ui.compose.infinitegrid.TileDefaults",
        "s.qs.panels.ui.compose.infinitegrid.TileKt",
        "s.qs.tiles.AirplaneModeTile",
        "s.qs.tiles.AlarmTile",
        "s.qs.tiles.BatterySaverTile",
        "s.qs.tiles.BluetoothTile",
        "s.qs.tiles.CameraToggleTile",
        "s.qs.tiles.CastTile",
        "s.qs.tiles.ColorCorrectionTile",
        "s.qs.tiles.ColorInversionTile",
        "s.qs.tiles.DataSaverTile",
        "s.qs.tiles.DeviceControlsTile",
        "s.qs.tiles.DndTile",
        "s.qs.tiles.DreamTile",
        "s.qs.tiles.FontScalingTile",
        "s.qs.tiles.HearingDevicesTile",
        "s.qs.tiles.HotspotTile",
        "s.qs.tiles.InternetTile",
        "s.qs.tiles.InternetTileNewImpl",
        "s.qs.tiles.LocationTile",
        "s.qs.tiles.MicrophoneToggleTile",
        "s.qs.tiles.ModesTile",
        "s.qs.tiles.NfcTile",
        "s.qs.tiles.NightDisplayTile",
        "s.qs.tiles.OneHandedModeTile",
        "s.qs.tiles.QRCodeScannerTile",
        "s.qs.tiles.QuickAccessWalletTile",
        "s.qs.tiles.RecordIssueTile",
        "s.qs.tiles.ReduceBrightColorsTile",
        "s.qs.tiles.RotationLockTile",
        "s.qs.tiles.ScreenRecordTile",
        "s.qs.tiles.SensorPrivacyToggleTile",
        "s.qs.tiles.UiModeNightTile",
        "s.qs.tiles.WorkModeTile",
        "s.qs.tiles.dialog.InternetDialogManager",
        "s.qs.ui.composable.QuickSettingsContentKt",
        "s.qs.ui.composable.QuickSettingsSceneKt",
        "s.qs.ui.composable.QuickSettingsShadeOverlayKt",
        "s.scene.ui.composable.transitions.ToSingleShadeTransitionKt",
        "s.scrim.ScrimView",
        "s.shade.LargeScreenShadeHeaderController",
        "s.shade.NotificationPanelViewController",
        "s.shade.QuickSettingsController",
        "s.shade.QuickSettingsControllerImpl",
        "s.shade.ShadeHeaderController",
        "s.shade.domain.interactor.ShadeInteractorImpl",
        "s.shade.ui.ShadeColors",
        "s.shade.ui.composable.ShadeHeaderKt",
        "s.shade.ui.composable.ShadePanelScrimKt",
        "s.shade.ui.composable.ShadeSceneKt",
        "s.shade.ui.composable.VariableDayDateKt",
        "s.shade.ui.viewmodel.ShadeSceneContentViewModel",
        "s.shared.rotation.FloatingRotationButton",
        "s.statusbar.LightRevealScrim",
        "s.statusbar.NotificationListener",
        "s.statusbar.ScalingDrawableWrapper",
        "s.statusbar.StatusBarIconView",
        "s.statusbar.StatusBarStateControllerImpl",
        "s.statusbar.connectivity.CallbackHandler",
        "s.statusbar.connectivity.MobileSignalController",
        "s.statusbar.connectivity.NetworkControllerImpl",
        "s.statusbar.notification.collection.NotificationEntry",
        "s.statusbar.notification.collection.NotificationEntryAdapter",
        "s.statusbar.notification.collection.coordinator.VisualStabilityCoordinator",
        "s.statusbar.notification.emptyshade.ui.view.EmptyShadeIconView",
        "s.statusbar.notification.footer.ui.view.FooterView",
        "s.statusbar.notification.headsup.HeadsUpManagerImpl",
        "s.statusbar.notification.row.ActivatableNotificationView",
        "s.statusbar.notification.row.ExpandableNotificationRow",
        "s.statusbar.notification.row.FooterView",
        "s.statusbar.notification.row.NotificationBackgroundView",
        "s.statusbar.notification.row.NotificationContentInflater",
        "s.statusbar.notification.row.NotificationContentView",
        "s.statusbar.notification.row.wrapper.NotificationHeaderViewWrapper",
        "s.statusbar.notification.row.wrapper.NotificationViewWrapper",
        "s.statusbar.notification.stack.SectionHeaderView",
        "s.statusbar.phone.CentralSurfacesImpl",
        "s.statusbar.phone.CollapsedStatusBarFragment",
        "s.statusbar.phone.ConfigurationControllerImpl",
        "s.statusbar.phone.DarkIconDispatcherImpl",
        "s.statusbar.phone.DozeScrimController",
        "s.statusbar.phone.HeadsUpAppearanceController",
        "s.statusbar.phone.KeyguardStatusBarView",
        "s.statusbar.phone.KeyguardStatusBarViewController",
        "s.statusbar.phone.LegacyNotificationIconAreaControllerImpl",
        "s.statusbar.phone.NotificationIconAreaController",
        "s.statusbar.phone.NotificationIconContainer",
        "s.statusbar.phone.PhoneStatusBarPolicy",
        "s.statusbar.phone.PhoneStatusBarView",
        "s.statusbar.phone.PhoneStatusBarViewController",
        "s.statusbar.phone.ScrimController",
        "s.statusbar.phone.StatusBarKeyguardViewManager",
        "s.statusbar.phone.fragment.CollapsedStatusBarFragment",
        "s.statusbar.phone.ui.IconManager",
        "s.statusbar.phone.ui.StatusBarIconControllerImpl",
        "s.statusbar.phone.ui.TintedIconManager",
        "s.statusbar.pipeline.battery.data.repository.BatteryCallbackState",
        "s.statusbar.pipeline.battery.shared.ui.BatteryColors",
        "s.statusbar.pipeline.battery.ui.composable.BatteryWithEstimateKt",
        "s.statusbar.pipeline.battery.ui.composable.UnifiedBatteryKt",
        "s.statusbar.pipeline.mobile.ui.compose.MobileIconDimensions",
        "s.statusbar.pipeline.mobile.ui.compose.MobileIconKt",
        "s.statusbar.pipeline.mobile.ui.view.ModernStatusBarMobileView",
        "s.statusbar.pipeline.shared.ui.composable.FiveBarsDimensions",
        "s.statusbar.pipeline.shared.ui.composable.FourBarsDimensions",
        "s.statusbar.pipeline.shared.ui.composable.StackedMobileIconDimensions",
        "s.statusbar.pipeline.shared.ui.composable.StackedMobileIconKt",
        "s.statusbar.pipeline.shared.ui.composable.StatusBarRootKt",
        "s.statusbar.pipeline.shared.ui.view.ModernStatusBarView",
        "s.statusbar.policy.BaseHeadsUpManager",
        "s.statusbar.policy.BatteryControllerImpl",
        "s.statusbar.policy.BluetoothControllerImpl",
        "s.statusbar.policy.Clock",
        "s.statusbar.policy.FlashlightControllerImpl",
        "s.statusbar.policy.HeadsUpManager",
        "s.statusbar.policy.HotspotControllerImpl",
        "s.statusbar.policy.KeyguardStateControllerImpl",
        "s.statusbar.systemstatusicons.data.repository.OrderedIconSlotNamesRepository",
        "s.statusbar.systemstatusicons.ui.compose.SystemStatusIconsKt",
        "s.util.drawable.DrawableSize",
        "s.volume.VolumeDialogImpl",
        "s.volume.dialog.settings.ui.binder.VolumeDialogSettingsButtonViewBinder",
        "s.volume.dialog.sliders.ui.VolumeDialogSlidersViewBinder",
        "s.volume.dialog.ui.binder.VolumeDialogViewBinder"
    )

    private fun full(s: String) = if (s.startsWith("s.")) "com.android.systemui." + s.substring(2) else if (s.startsWith("k.")) "com.android.keyguard." + s.substring(2) else s

    fun probe(x: XposedModule, cl: ClassLoader) {
        try {
            val miss = ArrayList<String>()
            var ok = 0
            for (c in classes) {
                val f = full(c)
                val e = try { Class.forName(f, false, cl); true } catch (t: Throwable) { false }
                if (e) ok++ else miss.add(c)
            }
            x.log(Log.INFO, TAG, "cls: ${cl.javaClass.simpleName} ok=$ok/${classes.size}")
            var i = 0
            while (i < miss.size) {
                val part = miss.subList(i, minOf(i + 25, miss.size)).joinToString(";")
                x.log(Log.INFO, TAG, "cls-no: $part")
                i += 25
            }
        } catch (e: Throwable) { }
    }

    private fun nm(v: View): String {
        if (v.id == View.NO_ID) return "-"
        return try { v.resources.getResourceEntryName(v.id) } catch (e: Throwable) { "?" }
    }

    fun view(x: XposedModule, v: View) {
        if (total >= CAP) return
        try {
            val cn = v.javaClass.name.removePrefix("com.android.systemui.")
            val name = nm(v)
            if (name == "-" && !v.javaClass.name.startsWith("com.android.")) return
            val key = "$name|$cn"
            if (!seen.add(key)) return
            total++
            val chain = StringBuilder()
            var p = v.parent
            var d = 0
            while (p is View && d < 3) { val pv = p as View; chain.append(nm(pv)).append('>'); p = pv.parent; d++ }
            buf.append(name).append('|').append(cn).append('|').append(chain).append(" ; ")
            n++
            if (n >= 30 || total >= CAP) {
                x.log(Log.INFO, TAG, "vw: " + buf)
                buf.setLength(0)
                n = 0
            }
        } catch (e: Throwable) { }
    }
}
