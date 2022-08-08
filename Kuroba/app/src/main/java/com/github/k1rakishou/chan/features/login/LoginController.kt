/*
 * KurobaEx - *chan browser https://github.com/K1rakishou/Kuroba-Experimental/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.k1rakishou.chan.features.login

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.core.text.parseAsHtml
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.di.component.activity.ActivityComponent
import com.github.k1rakishou.chan.core.manager.PostingLimitationsInfoManager
import com.github.k1rakishou.chan.core.manager.SiteManager
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginResponse
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginRequest
import com.github.k1rakishou.chan.core.site.http.login.DvachLoginRequest
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.features.bypass.CookieResult
import com.github.k1rakishou.chan.features.bypass.FirewallType
import com.github.k1rakishou.chan.features.bypass.SiteFirewallBypassController
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableButton
import com.github.k1rakishou.chan.ui.theme.widget.ColorizableEditText
import com.github.k1rakishou.chan.ui.view.CrossfadeView
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.getString
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.inflate
import com.github.k1rakishou.chan.utils.AppModuleAndroidUtils.waitForLayout
import com.github.k1rakishou.common.AndroidUtils
import com.github.k1rakishou.common.errorMessageOrClassName
import com.github.k1rakishou.core_themes.ThemeEngine
import kotlinx.coroutines.launch
import javax.inject.Inject

class LoginController(
  context: Context,
  private val site: Site
) : Controller(context), View.OnClickListener, ThemeEngine.ThemeChangesListener, LoginView {

  @Inject
  lateinit var themeEngine: ThemeEngine
  @Inject
  lateinit var postingLimitationsInfoManager: PostingLimitationsInfoManager
  @Inject
  lateinit var siteManager: SiteManager

  private lateinit var crossfadeView: CrossfadeView
  private lateinit var errors: TextView
  private lateinit var authenticated: TextView
  private lateinit var bottomDescription: TextView

  private lateinit var button: ColorizableButton
  private lateinit var refreshPostingLimitsInfoButton: ColorizableButton
  private lateinit var inputToken: ColorizableEditText
  private lateinit var inputPin: ColorizableEditText

  private val loginPresenter by lazy { LoginPresenter(postingLimitationsInfoManager) }

  override fun injectDependencies(component: ActivityComponent) {
    component.inject(this)
  }

  override fun onCreate() {
    super.onCreate()

    navigation.setTitle(getString(R.string.settings_screen_pass_title, site.name()))
    
    view = inflate(context, R.layout.controller_pass).also { view ->
      crossfadeView = view.findViewById(R.id.crossfade)
      errors = view.findViewById(R.id.errors)
      button = view.findViewById(R.id.retry_button)
      refreshPostingLimitsInfoButton = view.findViewById(R.id.refresh_posting_limits_info)
      inputToken = view.findViewById(R.id.input_token)
      inputPin = view.findViewById(R.id.input_pin)
      authenticated = view.findViewById(R.id.authenticated)
      bottomDescription = view.findViewById(R.id.bottom_description)
    }

    errors.visibility = View.GONE
    showBottomDescription()

    val loggedIn = loggedIn()
    val loggedInText = if (loggedIn) {
      R.string.setting_pass_logout
    } else {
      R.string.setting_pass_login
    }

    applyLoginDetails()

    button.setText(loggedInText)
    button.setOnClickListener(this)

    // Sanity check
    requireNotNull(parentController?.view?.windowToken) { "parentController.view not attached" }

    waitForLayout(view.viewTreeObserver, view) {
      crossfadeView.layoutParams.height = crossfadeView.height
      crossfadeView.requestLayout()
      crossfadeView.toggle(!loggedIn, false)
      false
    }

    themeEngine.addListener(this)
    onThemeChanged()

    loginPresenter.onCreate(this)
  }

  override fun onDestroy() {
    super.onDestroy()

    loginPresenter.onDestroy()
    themeEngine.removeListener(this)
  }

  override fun onThemeChanged() {
    authenticated.setTextColor(themeEngine.chanTheme.textColorPrimary)
    crossfadeView.setBackgroundColor(themeEngine.chanTheme.backColor)

    bottomDescription.setTextColor(themeEngine.chanTheme.textColorPrimary)
    bottomDescription.setLinkTextColor(themeEngine.chanTheme.postLinkColor)
  }

  override fun onRefreshPostingLimitsInfoError(error: Throwable) {
    showToast(error.errorMessageOrClassName())

    enableDisableControls(enable = true)
  }

  override fun onRefreshPostingLimitsInfoResult(refreshed: Boolean) {
    if (refreshed) {
      showToast(R.string.setting_posting_limits_info_refresh_success)
    } else {
      showToast(R.string.setting_posting_limits_info_refresh_failure)
    }

    enableDisableControls(enable = true)
  }

  private fun applyLoginDetails() {
    val loginDetails = requireNotNull(site.actions().loginDetails()) { "loginDetails is null" }
    if (loginDetails.type != SiteActions.LoginType.TokenAndPass) {
      inputPin.visibility = View.GONE
    } else {
      inputPin.visibility = View.VISIBLE
    }

    when (loginDetails) {
      is Chan4LoginRequest -> {
        inputToken.setText(loginDetails.user)
        inputToken.setHint(R.string.setting_pass_token)

        inputPin.setText(loginDetails.pass)
        inputPin.setHint(R.string.setting_pass_pin)
      }
      is DvachLoginRequest -> {
        inputToken.setText(loginDetails.passcode)
        inputToken.setHint(R.string.setting_passcode)
      }
    }

    if (loginDetails.loginOverridesPostLimitations) {
      refreshPostingLimitsInfoButton.visibility = View.VISIBLE
      refreshPostingLimitsInfoButton.setOnClickListener {
        if (!loggedIn()) {
          showToast(context.getString(R.string.must_be_logged_in))
          return@setOnClickListener
        }

        enableDisableControls(enable = false)
        loginPresenter.refreshPostingLimitsInfo(site.siteDescriptor())
      }
    }
  }

  private fun enableDisableControls(enable: Boolean) {
    button.isEnabled = enable
    refreshPostingLimitsInfoButton.isEnabled = enable
    inputToken.isEnabled = enable
    inputPin.isEnabled = enable
  }

  private fun showBottomDescription() {
    if (site is Chan4) {
      bottomDescription.text = getString(R.string.setting_pass_bottom_description_chan4).parseAsHtml()
      bottomDescription.movementMethod = LinkMovementMethod.getInstance()
      return
    } else if (site is Dvach) {
      bottomDescription.text = getString(R.string.setting_pass_bottom_description_dvach)
      return
    }

    bottomDescription.visibility = View.GONE
  }

  override fun onClick(v: View) {
    if (v === button) {
      if (loggedIn()) {
        deauth()
        crossfadeView.toggle(true, true)
        button.setText(R.string.setting_pass_login)
        hideError()
      } else {
        mainScope.launch { auth() }
      }
    }
  }
  
  private suspend fun auth(retrying: Boolean = false) {
    AndroidUtils.hideKeyboard(view)
    
    inputToken.isEnabled = false
    inputPin.isEnabled = false
    button.isEnabled = false
    button.setText(R.string.loading)
    
    hideError()

    when (val loginResult = site.actions().login(createLoginRequest())) {
      is SiteActions.LoginResult.LoginComplete -> {
        onLoginComplete(loginResult.loginResponse)
      }
      is SiteActions.LoginResult.LoginError -> {
        onLoginError(loginResult.errorMessage)
      }
      SiteActions.LoginResult.CloudflareDetected,
      SiteActions.LoginResult.AntiSpamDetected -> {
        if (retrying) {
          onLoginError("Firewall passed, now try logging in again")
          return
        }

        val firewallType = if (loginResult is SiteActions.LoginResult.CloudflareDetected) {
          FirewallType.Cloudflare
        } else {
          FirewallType.DvachAntiSpam
        }

        handleAntiSpam(firewallType)
      }
    }
  }

  private fun handleAntiSpam(firewallType: FirewallType) {
    val siteDescriptor = site.siteDescriptor()

    if (siteDescriptor.isDvach()) {
      val urlToOpen = siteManager.bySiteDescriptor(siteDescriptor)?.firewallChallengeEndpoint()
        ?: return

      val controller = SiteFirewallBypassController(
        context = context,
        firewallType = firewallType,
        urlToOpen = urlToOpen,
        onResult = { cookieResult ->
          if (cookieResult is CookieResult.CookieValue) {
            mainScope.launch { auth(retrying = true) }
            return@SiteFirewallBypassController
          }

          val error = when (cookieResult) {
            is CookieResult.CookieValue -> throw IllegalStateException("Must not be used here")
            CookieResult.Canceled -> getString(R.string.canceled)
            CookieResult.NotSupported -> "Not supported"
            is CookieResult.Error -> cookieResult.exception.errorMessageOrClassName()
          }

          onLoginError("Failed to pass anti-spam check, error=$error")
        }
      )

      presentController(controller)
      return
    }

    onLoginError("Unsupported anti-spam system detected!")
  }

  private fun createLoginRequest(): AbstractLoginRequest {
    return when (site) {
      is Chan4 -> {
        val user = inputToken.text.toString()
        val pass = inputPin.text.toString()

        Chan4LoginRequest(user, pass)
      }
      is Dvach -> {
        val passcode = inputToken.text.toString()

        DvachLoginRequest(passcode)
      }
      else -> throw NotImplementedError("Not supported")
    }
  }

  private fun onLoginComplete(loginResponse: AbstractLoginResponse) {
    if (loginResponse.isSuccess()) {
      authSuccess(loginResponse)
    } else {
      authFail(loginResponse.errorMessage())
    }
    
    authAfter()
  }
  
  private fun onLoginError(errorMessage: String) {
    authFail(errorMessage)
    authAfter()
  }
  
  private fun authSuccess(response: AbstractLoginResponse) {
    crossfadeView.toggle(false, true)
    button.setText(R.string.setting_pass_logout)
    authenticated.text = response.successMessage() ?: ""
  }
  
  private fun authFail(errorMessage: String?) {
    val message = errorMessage ?: getString(R.string.setting_pass_error)
    
    showError(message)
    button.setText(R.string.setting_pass_login)
  }
  
  private fun authAfter() {
    button.isEnabled = true
    inputToken.isEnabled = true
    inputPin.isEnabled = true
  }
  
  private fun deauth() {
    site.actions().logout()
  }
  
  private fun showError(error: String) {
    errors.text = error
    errors.visibility = View.VISIBLE
  }
  
  private fun hideError() {
    errors.text = null
    errors.visibility = View.GONE
  }
  
  private fun loggedIn(): Boolean {
    return site.actions().isLoggedIn()
  }
}