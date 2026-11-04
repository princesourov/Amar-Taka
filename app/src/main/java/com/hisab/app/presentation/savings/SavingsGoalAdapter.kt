package com.hisab.app.presentation.savings

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hisab.app.R
import com.hisab.app.data.repository.GoalOverview
import com.hisab.app.data.repository.GoalWithProgress
import com.hisab.app.databinding.ItemGoalsOverviewBinding
import com.hisab.app.databinding.ItemSavingsGoalBinding
import com.hisab.app.domain.goal.GoalStatus
import com.hisab.app.utils.formatMinorAsCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Rows of the goals list: a summary card first, then one row per goal. */
sealed interface GoalListItem {
    data class Summary(val savedMinor: Long, val freeMinor: Long) : GoalListItem
    data class Goal(val item: GoalWithProgress) : GoalListItem
}

fun GoalOverview.toListItems(): List<GoalListItem> =
    if (goals.isEmpty()) emptyList()
    else listOf<GoalListItem>(GoalListItem.Summary(totalAllocatedMinor, accounts.sumOf { it.availableMinor })) +
        goals.map { GoalListItem.Goal(it) }

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
fun formatEpochDay(day: Long): String = LocalDate.ofEpochDay(day).format(DATE_FMT)

class SavingsGoalAdapter(
    private val onGoalClick: (GoalWithProgress) -> Unit
) : ListAdapter<GoalListItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int) = if (getItem(position) is GoalListItem.Summary) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) SummaryVH(ItemGoalsOverviewBinding.inflate(inflater, parent, false))
        else GoalVH(ItemSavingsGoalBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is GoalListItem.Summary -> (holder as SummaryVH).bind(item)
            is GoalListItem.Goal -> (holder as GoalVH).bind(item.item, onGoalClick)
        }
    }

    class SummaryVH(private val b: ItemGoalsOverviewBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(s: GoalListItem.Summary) {
            b.overviewSaved.text = formatMinorAsCurrency(s.savedMinor)
            b.overviewAvailable.text = formatMinorAsCurrency(s.freeMinor)
        }
    }

    class GoalVH(private val b: ItemSavingsGoalBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(g: GoalWithProgress, onClick: (GoalWithProgress) -> Unit) {
            val ctx = b.root.context
            b.goalIcon.text = goalIconOrDefault(g.goal.iconKey)
            b.goalName.text = g.goal.name
            b.goalProgress.progress = g.percent
            b.goalPercent.text = ctx.getString(R.string.goal_percent, g.percent)
            b.goalAmounts.text = ctx.getString(R.string.goal_saved_target, formatMinorAsCurrency(g.savedMinor), formatMinorAsCurrency(g.goal.targetAmountMinor))
            b.goalRemaining.text = ctx.getString(R.string.goal_remaining, formatMinorAsCurrency(g.remainingMinor))
            b.goalStatus.text = goalStatusLine(ctx, g)
            b.root.setOnClickListener { onClick(g) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<GoalListItem>() {
            override fun areItemsTheSame(a: GoalListItem, b: GoalListItem) = when {
                a is GoalListItem.Summary && b is GoalListItem.Summary -> true
                a is GoalListItem.Goal && b is GoalListItem.Goal -> a.item.goal.id == b.item.goal.id
                else -> false
            }
            override fun areContentsTheSame(a: GoalListItem, b: GoalListItem) = a == b
        }
    }
}

fun goalStatusLine(ctx: android.content.Context, g: GoalWithProgress): String {
    val status = ctx.getString(
        when (g.status()) {
            GoalStatus.COMPLETED -> R.string.goal_status_completed
            GoalStatus.OVERDUE -> R.string.goal_status_overdue
            GoalStatus.IN_PROGRESS -> R.string.goal_status_in_progress
        }
    )
    val date = g.goal.targetDateEpochDay?.let { ctx.getString(R.string.goal_target_date_line, formatEpochDay(it)) }
        ?: ctx.getString(R.string.goal_no_target_date)
    return "$status · $date"
}
