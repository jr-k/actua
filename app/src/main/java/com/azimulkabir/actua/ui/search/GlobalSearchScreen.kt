package com.azimulkabir.actua.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.R
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.ui.transactions.TransactionDetailsSheet
import com.azimulkabir.actua.ui.transactions.TransactionRow

private enum class SearchFilter(val labelRes: Int) {
    ALL(R.string.search_filter_all),
    TRANSACTIONS(R.string.search_filter_transactions),
    ACCOUNTS(R.string.search_filter_accounts),
    PAYEES(R.string.search_filter_payees),
    CATEGORIES(R.string.search_filter_categories),
}

@Composable
fun GlobalSearchScreen(
    transactions: List<Transaction>,
    searchTransactions: suspend (String, Int, Int) -> List<Transaction>,
    accounts: List<Account>,
    payees: List<String>,
    categories: List<String>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onTransactionEdit: (Transaction) -> Unit,
    onTransactionDelete: (Transaction) -> Unit,
    onTransactionClearedChange: (Transaction, Boolean) -> Unit,
    onAccountClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onPayeeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    val term = query.trim()
    var page by remember(term, transactions) { mutableStateOf(0) }
    var matchingTransactions by remember(term, transactions) { mutableStateOf(emptyList<Transaction>()) }
    var loading by remember(term, transactions) { mutableStateOf(term.isNotBlank()) }
    var hasMore by remember(term, transactions) { mutableStateOf(false) }
    var searchFailed by remember(term, transactions) { mutableStateOf(false) }
    LaunchedEffect(term, page, transactions, searchTransactions) {
        if (term.isBlank()) return@LaunchedEffect
        loading = true
        searchFailed = false
        try {
            if (page == 0) delay(200)
            val results = searchTransactions(term, 51, page * 50)
            matchingTransactions = (if (page == 0) results.take(50)
                else matchingTransactions + results.take(50)).distinctBy { it.id }
            hasMore = results.size > 50
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            searchFailed = true
        } finally {
            loading = false
        }
    }
    val matchingAccounts = remember(term, accounts) { if (term.isBlank()) emptyList() else accounts.filter { it.name.contains(term, true) } }
    val matchingPayees = remember(term, payees) { if (term.isBlank()) emptyList() else payees.filter { it.contains(term, true) }.take(20) }
    val matchingCategories = remember(term, categories) { if (term.isBlank()) emptyList() else categories.filter { it.contains(term, true) }.take(20) }
    val transactionsLabel = stringResource(R.string.search_filter_transactions)
    val accountsLabel = stringResource(R.string.search_filter_accounts)
    val payeesLabel = stringResource(R.string.search_filter_payees)
    val categoriesLabel = stringResource(R.string.search_filter_categories)

    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_title)) },
            leadingIcon = { IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
            } },
            trailingIcon = { Icon(Icons.Outlined.Search, stringResource(R.string.search_action)) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            items(SearchFilter.entries) { item ->
                FilterChip(selected = filter == item, onClick = { filter = item }, label = {
                    Text(stringResource(item.labelRes))
                })
            }
        }
        if (term.isBlank()) {
            Text(stringResource(R.string.search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (filter == SearchFilter.ALL || filter == SearchFilter.TRANSACTIONS) {
                    searchSection(transactionsLabel, matchingTransactions) { transaction ->
                        TransactionRow(
                            transaction = transaction,
                            hideDecimalPlaces = hideDecimalPlaces,
                            showDate = true,
                            showAccount = true,
                            onClick = { selectedTransaction = transaction },
                            onLongClick = { selectedTransaction = transaction },
                            onClearedClick = { onTransactionClearedChange(transaction, !transaction.cleared) },
                        )
                    }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.TRANSACTIONS) {
                    item("transaction-search-status") {
                        when {
                            loading -> Text(stringResource(R.string.search_searching), modifier = Modifier.padding(20.dp))
                            searchFailed -> Text(stringResource(R.string.search_failed), modifier = Modifier.padding(20.dp))
                            hasMore -> TextButton(onClick = { page += 1 }) {
                                Text(stringResource(R.string.search_load_more_transactions))
                            }
                            matchingTransactions.isEmpty() -> Text(
                                stringResource(R.string.search_no_matching_transactions),
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.ACCOUNTS) {
                    searchSection(accountsLabel, matchingAccounts) { account ->
                        SearchRow(
                            account.name.ifBlank { stringResource(R.string.common_unknown) },
                            account.type,
                            onClick = { onAccountClick(account.name) },
                        )
                    }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.PAYEES) {
                    searchSection(payeesLabel, matchingPayees) { payee ->
                        SearchRow(payee, onClick = { onPayeeClick(payee) })
                    }
                }
                if (filter == SearchFilter.ALL || filter == SearchFilter.CATEGORIES) {
                    searchSection(categoriesLabel, matchingCategories) { category ->
                        SearchRow(
                            category.ifBlank { stringResource(R.string.common_unknown) },
                            onClick = { onCategoryClick(category) },
                        )
                    }
                }
            }
        }
    }
    selectedTransaction?.let { transaction ->
        TransactionDetailsSheet(
            transaction = transaction,
            hideDecimalPlaces = hideDecimalPlaces,
            onDismiss = { selectedTransaction = null },
            onEdit = { selectedTransaction = null; onTransactionEdit(transaction) },
            onDelete = { selectedTransaction = null; onTransactionDelete(transaction) },
        )
    }
}

private fun <T> androidx.compose.foundation.lazy.LazyListScope.searchSection(
    title: String,
    values: List<T>,
    content: @Composable (T) -> Unit,
) {
    if (values.isEmpty()) return
    item("header-$title") {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
    }
    items(values) { value -> content(value) }
}

@Composable
private fun SearchRow(title: String, subtitle: String = "", trailing: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Text(it, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp)) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
}
