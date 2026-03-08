package com.github.k1rakishou.chan.core.site.settings

class SiteSettingsForUi : Collection<SiteSettingForUi> {
  private val _settings = mutableListOf<SiteSettingForUi>()

  override val size: Int
    get() = _settings.size

  constructor(other: SiteSettingsForUi) {
    _settings.addAll(other._settings)
  }

  constructor()

  operator fun plusAssign(single: SiteSettingForUi) {
    _settings.add(single)
  }

  override fun iterator(): Iterator<SiteSettingForUi> {
    return IteratorImpl(_settings.toList())
  }

  override fun contains(element: SiteSettingForUi): Boolean {
    error("Not supported")
  }

  override fun containsAll(elements: Collection<SiteSettingForUi>): Boolean {
    error("Not supported")
  }

  override fun isEmpty(): Boolean {
    return _settings.isEmpty()
  }

  class IteratorImpl(
    private val settings: List<SiteSettingForUi>
  ) : Iterator<SiteSettingForUi> {
    private var _index = 0

    override fun hasNext(): Boolean {
      return settings.getOrNull(_index) != null
    }

    override fun next(): SiteSettingForUi {
      return settings.getOrNull(_index++)
        ?: throw NoSuchElementException("Index: ${_index}, elements count: ${settings.size}")
    }
  }
}