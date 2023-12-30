package com.github.k1rakishou.model.mapper

import com.github.k1rakishou.model.data.descriptor.BoardDescriptor
import com.github.k1rakishou.model.data.filter.ChanFilter
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterBoardConstraintEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterEntity
import com.github.k1rakishou.model.entity.chan.filter.ChanFilterFull

object ChanFilterMapper {

  fun fromEntity(chanFilterFull: ChanFilterFull): ChanFilter {
    val boardDescriptors = chanFilterFull.chanFilterBoardConstraintEntityList.map { chanFilterBoardConstraintEntity ->
      return@map BoardDescriptor.create(
        chanFilterBoardConstraintEntity.siteNameConstraint,
        chanFilterBoardConstraintEntity.boardCodeConstraint
      )
    }

    return ChanFilter(
      filterDatabaseId = chanFilterFull.chanFilterEntity.filterId,
      enabled = chanFilterFull.chanFilterEntity.enabled,
      type = chanFilterFull.chanFilterEntity.type,
      pattern = chanFilterFull.chanFilterEntity.pattern,
      boards = boardDescriptors.toSet(),
      action = chanFilterFull.chanFilterEntity.action,
      color = chanFilterFull.chanFilterEntity.color,
      note = chanFilterFull.chanFilterEntity.note,
      applyToReplies = chanFilterFull.chanFilterEntity.applyToReplies,
      onlyOnOP = chanFilterFull.chanFilterEntity.onlyOnOP,
      applyToSaved = chanFilterFull.chanFilterEntity.applyToSaved,
      applyToEmptyComments = chanFilterFull.chanFilterEntity.applyToEmptyComments,
      filterWatchNotify = chanFilterFull.chanFilterEntity.filterWatchNotify,
      filterWatchAutoSave = chanFilterFull.chanFilterEntity.filterWatchAutoSave,
      filterWatchAutoSaveMedia = chanFilterFull.chanFilterEntity.filterWatchAutoSaveMedia,
    )
  }

  fun toEntity(chanFilter: ChanFilter, order: Int): ChanFilterFull {
    return ChanFilterFull(
      chanFilterEntity = ChanFilterEntity(
        filterId = chanFilter.getDatabaseId(),
        enabled = chanFilter.enabled,
        filterOrder = order,
        type = chanFilter.type,
        pattern = chanFilter.pattern,
        action = chanFilter.action,
        color = chanFilter.color,
        note = chanFilter.note,
        applyToReplies = chanFilter.applyToReplies,
        onlyOnOP = chanFilter.onlyOnOP,
        applyToSaved = chanFilter.applyToSaved,
        applyToEmptyComments = chanFilter.applyToEmptyComments,
        filterWatchNotify = chanFilter.filterWatchNotify,
        filterWatchAutoSave = chanFilter.filterWatchAutoSave,
        filterWatchAutoSaveMedia = chanFilter.filterWatchAutoSaveMedia,
      ),
      chanFilterBoardConstraintEntityList = chanFilter.boards.map { boardDescriptor ->
        ChanFilterBoardConstraintEntity(
          ownerFilterId = chanFilter.getDatabaseId(),
          siteNameConstraint = boardDescriptor.siteName(),
          boardCodeConstraint = boardDescriptor.boardCode
        )
      }
    )
  }

}