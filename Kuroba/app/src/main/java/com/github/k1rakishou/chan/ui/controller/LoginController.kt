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
package com.github.k1rakishou.chan.ui.controller

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.text.parseAsHtml
import com.github.k1rakishou.chan.R
import com.github.k1rakishou.chan.controller.Controller
import com.github.k1rakishou.chan.core.site.Site
import com.github.k1rakishou.chan.core.site.SiteActions
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginRequest
import com.github.k1rakishou.chan.core.site.http.login.AbstractLoginResponse
import com.github.k1rakishou.chan.core.site.http.login.Chan4LoginRequest
import com.github.k1rakishou.chan.core.site.http.login.DvachLoginRequest
import com.github.k1rakishou.chan.core.site.sites.chan4.Chan4
import com.github.k1rakishou.chan.core.site.sites.dvach.Dvach
import com.github.k1rakishou.chan.ui.view.CrossfadeView
import com.github.k1rakishou.chan.utils.AndroidUtils
import kotlinx.coroutines.launch

class LoginController(
  context: Context,
  private val site: Site
) : Controller(context), View.OnClickListener {
  
  private lateinit var crossfadeView: CrossfadeView
  private lateinit var errors: TextView
  private lateinit var button: Button
  private lateinit var inputToken: EditText
  private lateinit var inputPin: EditText
  private lateinit var authenticated: TextView
  
  override fun onCreate() {
    super.onCreate()
    navigation.setTitle(R.string.settings_screen_pass)
    
    view = AndroidUtils.inflate(context, R.layout.controller_pass).also { view ->
      crossfadeView = view.findViewById(R.id.crossfade)
      errors = view.findViewById(R.id.errors)
      button = view.findViewById(R.id.button)
      inputToken = view.findViewById(R.id.input_token)
      inputPin = view.findViewById(R.id.input_pin)
      authenticated = view.findViewById(R.id.authenticated)
      errors.visibility = View.GONE

      showBottomDescription(view)

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
  
      AndroidUtils.waitForLayout(view.viewTreeObserver, view) {
        crossfadeView.layoutParams.height = crossfadeView.height
        crossfadeView.requestLayout()
        crossfadeView.toggle(!loggedIn, false)
        false
      }
    }
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
  }

  private fun showBottomDescription(view: ViewGroup) {
    val bottomDescription = view.findViewById<TextView>(R.id.bottom_description)

    if (site is Chan4) {
      bottomDescription.text = AndroidUtils.getString(R.string.setting_pass_bottom_description).parseAsHtml()
      bottomDescription.movementMethod = LinkMovementMethod.getInstance()
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
  
  private suspend fun auth() {
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
    }
  }

  private fun createLoginRequest(): AbstractLoginRequest {
    when (site) {
      is Chan4 -> {
        val user = inputToken.text.toString()
        val pass = inputPin.text.toString()

        return Chan4LoginRequest(user, pass)
      }
      is Dvach -> {
        val passcode = inputToken.text.toString()

        return DvachLoginRequest(passcode)
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
    val message = errorMessage ?: AndroidUtils.getString(R.string.setting_pass_error)
    
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