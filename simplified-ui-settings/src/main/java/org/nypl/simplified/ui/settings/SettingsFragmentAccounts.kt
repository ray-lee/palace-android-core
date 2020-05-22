package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import io.reactivex.disposables.Disposable
import org.librarysimplified.services.api.Services
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation
import org.nypl.simplified.accounts.api.AccountEventDeletion.AccountEventDeletionFailed
import org.nypl.simplified.accounts.api.AccountEventDeletion.AccountEventDeletionSucceeded
import org.nypl.simplified.accounts.api.AccountEventUpdated
import org.nypl.simplified.accounts.database.api.AccountType
import org.nypl.simplified.buildconfig.api.BuildConfigurationServiceType
import org.nypl.simplified.navigation.api.NavigationControllers
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.errorpage.ErrorPageParameters
import org.nypl.simplified.ui.images.ImageLoaderType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.nypl.simplified.ui.toolbar.ToolbarHostType

/**
 * A fragment that shows the set of accounts in the current profile.
 */

class SettingsFragmentAccounts : Fragment() {

  private lateinit var accountList: RecyclerView
  private lateinit var accountListAdapter: SettingsAccountsAdapter
  private lateinit var accountListData: MutableList<AccountType>
  private lateinit var buildConfig: BuildConfigurationServiceType
  private lateinit var imageLoader: ImageLoaderType
  private lateinit var profilesController: ProfilesControllerType
  private lateinit var uiThread: UIThreadServiceType
  private var accountSubscription: Disposable? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    this.accountListData = mutableListOf()

    val services = Services.serviceDirectory()

    this.profilesController =
      services.requireService(ProfilesControllerType::class.java)
    this.imageLoader =
      services.requireService(ImageLoaderType::class.java)
    this.uiThread =
      services.requireService(UIThreadServiceType::class.java)
    this.buildConfig =
      services.requireService(BuildConfigurationServiceType::class.java)
  }

  @UiThread
  private fun onAccountLongClicked(account: AccountType) {
    this.uiThread.checkIsUIThread()

    val context = this.requireContext()
    AlertDialog.Builder(context)
      .setTitle(R.string.settingsAccountDeleteConfirmTitle)
      .setMessage(
        context.getString(
          R.string.settingsAccountDeleteConfirm,
          account.provider.displayName
        )
      )
      .setPositiveButton(R.string.settingsAccountDelete) { dialog, _ ->
        this.profilesController.profileAccountDeleteByProvider(account.provider.id)
        dialog.dismiss()
      }
      .create()
      .show()
  }

  @UiThread
  private fun onAccountClicked(account: AccountType) {
    this.uiThread.checkIsUIThread()
    this.findNavigationController().openSettingsAccount(account.id)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layout =
      inflater.inflate(R.layout.settings_accounts, container, false)

    this.accountList =
      layout.findViewById(R.id.accountList)

    this.accountList.setHasFixedSize(true)
    this.accountList.layoutManager = LinearLayoutManager(this.context)
    (this.accountList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
    return layout
  }

  override fun onStart() {
    super.onStart()

    this.configureToolbar()

    this.accountSubscription =
      this.profilesController.accountEvents()
        .subscribe(this::onAccountEvent)

    this.accountListAdapter =
      SettingsAccountsAdapter(
        accounts = this.accountListData,
        imageLoader = this.imageLoader,
        onItemClicked = { account -> this.onAccountClicked(account) },
        onItemLongClicked = { account -> this.onAccountLongClicked(account) })

    this.accountList.adapter = this.accountListAdapter

    this.uiThread.runOnUIThread(Runnable {
      this.reconfigureAccountListUI()
    })
  }

  private fun configureToolbar() {
    val host = this.activity
    if (host is ToolbarHostType) {
      val toolbar = host.findToolbar()

      host.toolbarClearMenu()
      toolbar.inflateMenu(R.menu.accounts)

      val accountAdd = toolbar.menu.findItem(R.id.settingsMenuActionAccountAdd)
      accountAdd.setOnMenuItemClickListener {
        this.findNavigationController().openSettingsAccountRegistry()
        true
      }

      host.toolbarSetTitleSubtitle(
        title = this.requireContext().getString(R.string.settingsAccounts),
        subtitle = ""
      )
      host.toolbarSetBackArrowConditionally(
        context = host,
        shouldArrowBePresent = {
          this.findNavigationController().backStackSize() > 1
        },
        onArrowClicked = {
          this.findNavigationController().popBackStack()
        })
    } else {
      throw IllegalStateException("The activity ($host) hosting this fragment must implement ${ToolbarHostType::class.java}")
    }
  }

  private fun onAccountEvent(accountEvent: AccountEvent) {
    return when (accountEvent) {
      is AccountEventCreation.AccountEventCreationSucceeded,
      is AccountEventDeletionSucceeded,
      is AccountEventUpdated -> {

        this.uiThread.runOnUIThread(Runnable {
          this.reconfigureAccountListUI()
        })
      }

      is AccountEventDeletionFailed -> {
        this.uiThread.runOnUIThread(Runnable {
          this.showAccountDeletionFailedDialog(accountEvent)
        })
      }

      else -> {
      }
    }
  }

  @UiThread
  private fun showAccountDeletionFailedDialog(accountEvent: AccountEventDeletionFailed) {
    this.uiThread.checkIsUIThread()

    AlertDialog.Builder(this.requireContext())
      .setTitle(R.string.settingsAccountDeletionFailed)
      .setMessage(R.string.settingsAccountDeletionFailedMessage)
      .setPositiveButton(R.string.settingsDetails) { _, _ ->
        showErrorPage(accountEvent)
      }
      .create()
      .show()
  }

  @UiThread
  private fun showErrorPage(accountEvent: AccountEventDeletionFailed) {
    this.uiThread.checkIsUIThread()

    val parameters =
      ErrorPageParameters(
        emailAddress = this.buildConfig.errorReportEmail,
        body = "",
        subject = "[simplye-error-report]",
        attributes = accountEvent.attributes.toSortedMap(),
        taskSteps = accountEvent.taskResult.steps
      )

    this.findNavigationController().openErrorPage(parameters)
  }

  @UiThread
  private fun reconfigureAccountListUI() {
    this.uiThread.checkIsUIThread()

    val profile =
      this.profilesController.profileCurrent()

    val accountList =
      profile
        .accounts()
        .values
        .sortedBy { account -> account.provider.displayName }

    this.accountListData.clear()
    this.accountListData.addAll(accountList)
    this.accountListAdapter.notifyDataSetChanged()
  }

  override fun onStop() {
    super.onStop()
    this.accountSubscription?.dispose()
  }

  private fun findNavigationController(): SettingsNavigationControllerType {
    return NavigationControllers.find(
      activity = this.requireActivity(),
      interfaceType = SettingsNavigationControllerType::class.java
    )
  }
}
