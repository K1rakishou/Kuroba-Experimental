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
package com.github.adamantcheese.chan.core.site.http.login


sealed class Chan4LoginResponse(authCookie: String?) : AbstractLoginResponse(authCookie) {
  override fun isSuccess(): Boolean = this is Chan4LoginResponse.Success
  override fun successMessage(): String? = (this as? Chan4LoginResponse.Success)?.successMessage
  override fun errorMessage(): String? = (this as? Chan4LoginResponse.Failure)?.errorMessage

  class Success(val successMessage: String, authCookie: String) : Chan4LoginResponse(authCookie)
  class Failure(val errorMessage: String) : Chan4LoginResponse(null)
}