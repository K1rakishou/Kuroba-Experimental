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
package com.github.adamantcheese.chan.ui.controller

import android.content.Context
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.github.adamantcheese.chan.R
import com.github.adamantcheese.chan.controller.Controller
import com.github.adamantcheese.chan.core.navigation.RequiresNoBottomNavBar
import com.github.adamantcheese.chan.core.site.Site
import com.github.adamantcheese.chan.core.site.SiteActions
import com.github.adamantcheese.chan.core.site.http.HttpCall
import com.github.adamantcheese.chan.core.site.http.LoginRequest
import com.github.adamantcheese.chan.core.site.http.LoginResponse
import com.github.adamantcheese.chan.ui.view.CrossfadeView
import com.github.adamantcheese.chan.utils.AndroidUtils
import kotlinx.coroutines.launch

class LoginController(
  context: Context,
  private val site: Site
) : Controller(context), View.OnClickListener, RequiresNoBottomNavBar {
  
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
      
      val bottomDescription = view.findViewById<TextView>(R.id.bottom_description)
      bottomDescription.text = Html.fromHtml(AndroidUtils.getString(R.string.setting_pass_bottom_description))
      bottomDescription.movementMethod = LinkMovementMethod.getInstance()
      

      val loggedIn = loggedIn()
      val loggedInText = if (loggedIn) {
        R.string.setting_pass_logout
      } else {
        R.string.setting_pass_login
      }
      
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
    
    val loginDetails = site.actions().loginDetails()!!
    inputToken.setText(loginDetails.user)
    inputPin.setText(loginDetails.pass)
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
    button.setText(R.string.setting_pass_logging_in)
    
    hideError()
    val user = inputToken.text.toString()
    val pass = inputPin.text.toString()
    
    when (val loginResult = site.actions().login(LoginRequest(user, pass))) {
      is SiteActions.LoginResult.LoginComplete -> {
        onLoginComplete(loginResult.httpCall, loginResult.loginResponse)
      }
      is SiteActions.LoginResult.LoginError -> {
        onLoginError(loginResult.httpCall)
      }
    }
  }
  
  private fun onLoginComplete(httpCall: HttpCall?, loginResponse: LoginResponse) {
    if (loginResponse.success) {
      authSuccess(loginResponse)
    } else {
      authFail(loginResponse)
    }
    
    authAfter()
  }
  
  private fun onLoginError(httpCall: HttpCall?) {
    authFail(null)
    authAfter()
  }
  
  private fun authSuccess(response: LoginResponse) {
    crossfadeView.toggle(false, true)
    button.setText(R.string.setting_pass_logout)
    authenticated.text = response.message
  }
  
  private fun authFail(response: LoginResponse?) {
    var message = AndroidUtils.getString(R.string.setting_pass_error)
    
    if (response?.message != null) {
      message = response.message
    }
    
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