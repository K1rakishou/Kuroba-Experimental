package com.github.k1rakishou.chan.core.site.settings

class SiteSettingsForUi : Collection<SiteSetting> {
  private val _settings = mutableListOf<SiteSetting>()

  override val size: Int
    get() = _settings.size

  constructor(other: SiteSettingsForUi) {
    _settings.addAll(other._settings)
  }

  constructor()

  operator fun plusAssign(single: SiteSetting) {
    _settings.add(single)
  }

  override fun iterator(): Iterator<SiteSetting> {
    return IteratorImpl(_settings.toList())
  }

  override fun contains(element: SiteSetting): Boolean {
    error("Not supported")
  }

  override fun containsAll(elements: Collection<SiteSetting>): Boolean {
    error("Not supported")
  }

  override fun isEmpty(): Boolean {
    return _settings.isEmpty()
  }

  class IteratorImpl(
    private val settings: List<SiteSetting>
  ) : Iterator<SiteSetting> {
    private var _index = 0

    override fun hasNext(): Boolean {
      return settings.getOrNull(_index) != null
    }

    override fun next(): SiteSetting {
      return settings.getOrNull(_index++)
        ?: throw NoSuchElementException("Index: ${_index}, elements count: ${settings.size}")
    }
  }
}