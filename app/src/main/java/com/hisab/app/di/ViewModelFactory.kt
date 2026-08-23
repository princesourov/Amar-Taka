package com.hisab.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hisab.app.presentation.accounts.AccountsViewModel
import com.hisab.app.presentation.budgets.BudgetsViewModel
import com.hisab.app.presentation.dashboard.DashboardViewModel
import com.hisab.app.presentation.expense.AddExpenseViewModel
import com.hisab.app.presentation.income.AddMoneyViewModel
import com.hisab.app.presentation.people.PeopleViewModel
import com.hisab.app.presentation.people.PersonDetailViewModel
import com.hisab.app.presentation.recurring.RecurringViewModel
import com.hisab.app.presentation.savings.SavingsGoalsViewModel
import com.hisab.app.presentation.transactions.EditTransactionViewModel
import com.hisab.app.presentation.transactions.TransactionsViewModel
import com.hisab.app.presentation.transfer.TransferMoneyViewModel

/**
 * [transactionId] is only needed by [EditTransactionViewModel] and [personId] only by
 * [PersonDetailViewModel] — screens that each revolve around one specific existing row.
 * Every other ViewModel here is parameterless beyond the container, so this stays a single
 * shared factory rather than one per screen.
 */
class ViewModelFactory(
    private val container: AppContainer,
    private val transactionId: String? = null,
    private val personId: String? = null
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            DashboardViewModel::class.java -> DashboardViewModel(
                container.userId,
                container.accountRepository,
                container.transactionRepository,
                container.personRepository,
                container.budgetRepository,
                container.savingsGoalRepository,
                container.categoryDao
            ) as T

            AddExpenseViewModel::class.java -> AddExpenseViewModel(
                container.userId,
                container.accountRepository,
                container.categoryDao,
                container.recordExpenseUseCase
            ) as T

            AddMoneyViewModel::class.java -> AddMoneyViewModel(
                container.userId,
                container.accountRepository,
                container.categoryDao,
                container.recordIncomeUseCase
            ) as T

            BudgetsViewModel::class.java -> BudgetsViewModel(
                container.userId,
                container.budgetRepository,
                container.categoryDao
            ) as T

            SavingsGoalsViewModel::class.java -> SavingsGoalsViewModel(
                container.userId,
                container.savingsGoalRepository
            ) as T

            RecurringViewModel::class.java -> RecurringViewModel(
                container.userId,
                container.recurringTransactionRepository,
                container.accountRepository,
                container.categoryDao
            ) as T

            AccountsViewModel::class.java -> AccountsViewModel(
                container.userId,
                container.accountRepository
            ) as T

            TransactionsViewModel::class.java -> TransactionsViewModel(
                container.userId,
                container.transactionRepository,
                container.accountRepository,
                container.categoryDao,
                container.personRepository,
                container.deleteTransactionUseCase
            ) as T

            EditTransactionViewModel::class.java -> EditTransactionViewModel(
                container.userId,
                requireNotNull(transactionId) { "transactionId is required to create an EditTransactionViewModel" },
                container.transactionRepository,
                container.accountRepository,
                container.categoryDao,
                container.editTransactionUseCase
            ) as T

            TransferMoneyViewModel::class.java -> TransferMoneyViewModel(
                container.userId,
                container.accountRepository,
                container.transferMoneyUseCase
            ) as T

            PeopleViewModel::class.java -> PeopleViewModel(
                container.userId,
                container.personRepository
            ) as T

            PersonDetailViewModel::class.java -> PersonDetailViewModel(
                container.userId,
                requireNotNull(personId) { "personId is required to create a PersonDetailViewModel" },
                container.personRepository,
                container.accountRepository,
                container.categoryDao,
                container.lendMoneyUseCase,
                container.borrowMoneyUseCase,
                container.recordRepaymentReceivedUseCase,
                container.recordRepaymentMadeUseCase,
                container.deleteTransactionUseCase
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
