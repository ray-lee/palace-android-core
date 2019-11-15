package org.nypl.simplified.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import org.librarysimplified.services.api.ServiceDirectoryProviderType
import org.nypl.simplified.accounts.api.AccountEvent
import org.nypl.simplified.accounts.api.AccountEventCreation
import org.nypl.simplified.accounts.api.AccountProviderDescriptionType
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryEvent
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryStatus
import org.nypl.simplified.accounts.registry.api.AccountProviderRegistryType
import org.nypl.simplified.observable.ObservableSubscriptionType
import org.nypl.simplified.profiles.api.ProfilePreferences
import org.nypl.simplified.profiles.controller.api.ProfilesControllerType
import org.nypl.simplified.ui.thread.api.UIThreadServiceType
import org.slf4j.LoggerFactory

/**
 * A fragment that shows settings for a single account.
 */

class SettingsFragmentAccountRegistry : Fragment() {

  private val logger = LoggerFactory.getLogger(SettingsFragmentAccountRegistry::class.java)

  private lateinit var navigation: SettingsNavigationControllerType
  private lateinit var accountList: RecyclerView
  private lateinit var accountListAdapter: SettingsAccountProviderDescriptionAdapter
  private lateinit var accountListData: MutableList<AccountProviderDescriptionType>
  private lateinit var accountRegistry: AccountProviderRegistryType
  private lateinit var host: ServiceDirectoryProviderType
  private lateinit var profilesController: ProfilesControllerType
  private lateinit var progress: ProgressBar
  private lateinit var progressText: TextView
  private lateinit var refresh: Button
  private lateinit var title: TextView
  private lateinit var uiThread: UIThreadServiceType
  private var accountCreationSubscription: ObservableSubscriptionType<AccountEvent>? = null
  private var accountRegistrySubscription: ObservableSubscriptionType<AccountProviderRegistryEvent>? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val context = this.requireContext()
    if (context is ServiceDirectoryProviderType) {
      this.host = context
    } else {
      throw IllegalStateException(
        "The context hosting this fragment must implement ${ServiceDirectoryProviderType::class.java}")
    }

    this.accountRegistry =
      this.host.serviceDirectory.requireService(AccountProviderRegistryType::class.java)
    this.navigation =
      this.host.serviceDirectory.requireService(SettingsNavigationControllerType::class.java)
    this.profilesController =
      this.host.serviceDirectory.requireService(ProfilesControllerType::class.java)
    this.uiThread =
      this.host.serviceDirectory.requireService(UIThreadServiceType::class.java)

    this.accountListData = mutableListOf()
    this.accountListAdapter =
      SettingsAccountProviderDescriptionAdapter(
        this.accountListData, this::onAccountClicked)
  }

  /**
   * Return a list of the available account providers. An account provider is available
   * if no account already exists for it in the current profile.
   */

  private fun determineAvailableAccountProviderDescriptions(): List<AccountProviderDescriptionType> {

    val profileCurrent =
      this.profilesController.profileCurrent()
    val preferences =
      profileCurrent.preferences()

    val usedAccountProviders =
      this.profilesController
        .profileCurrentlyUsedAccountProviders()
        .map { p -> p.toDescription() }

    this.logger.debug("should show testing providers: {}", preferences.showTestingLibraries())
    this.logger.debug("profile is using {} providers", usedAccountProviders.size)

    val availableAccountProviders =
      this.accountRegistry.accountProviderDescriptions()
        .values
        .filter { provider -> shouldShowProvider(provider, preferences) }
        .toMutableList()

    availableAccountProviders.removeAll(usedAccountProviders)
    availableAccountProviders.sortWith(Comparator { provider0, provider1 ->
      val name0 = provider0.metadata.title.removePrefix("The ")
      val name1 = provider1.metadata.title.removePrefix("The ")
      name0.toUpperCase().compareTo(name1.toUpperCase())
    })

    this.logger.debug("returning {} available providers", availableAccountProviders.size)
    return availableAccountProviders
  }

  private fun shouldShowProvider(
    provider: AccountProviderDescriptionType,
    preferences: ProfilePreferences
  ) =
    provider.metadata.isProduction || preferences.showTestingLibraries()

  @UiThread
  private fun onAccountClicked(account: AccountProviderDescriptionType) {
    this.uiThread.checkIsUIThread()

    this.logger.debug("selected account: {} ({})", account.metadata.id, account.metadata.title)

    this.refresh.isEnabled = false
    this.accountList.visibility = View.INVISIBLE
    this.title.setText(R.string.settingsAccountRegistryCreating)
    this.progressText.text = ""
    this.progress.visibility = View.VISIBLE

    this.profilesController.profileAccountCreate(account.metadata.id)
  }

  private fun onAccountEvent(event: AccountEvent) {
    return when (event) {
      is AccountEventCreation.AccountEventCreationInProgress ->
        this.uiThread.runOnUIThread(Runnable {
          this.accountList.visibility = View.INVISIBLE
          this.progress.visibility = View.VISIBLE
          this.progressText.text = event.message
        })

      is AccountEventCreation.AccountEventCreationSucceeded ->
        this.navigation.popBackStack()

      is AccountEventCreation.AccountEventCreationFailed ->
        this.uiThread.runOnUIThread(Runnable {
          this.reconfigureViewForRegistryStatus(this.accountRegistry.status)
        })

      else -> {

      }
    }
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val layout =
      inflater.inflate(R.layout.settings_account_registry, container, false)

    this.refresh =
      layout.findViewById(R.id.accountRegistryRefreshButton)
    this.title =
      layout.findViewById(R.id.accountRegistryTitle)
    this.progress =
      layout.findViewById(R.id.accountRegistryProgress)
    this.progressText =
      layout.findViewById(R.id.accountRegistryProgressText)
    this.accountList =
      layout.findViewById(R.id.accountRegistryList)

    this.accountList.setHasFixedSize(true)
    this.accountList.layoutManager = LinearLayoutManager(this.context)
    this.accountList.adapter = this.accountListAdapter
    (this.accountList.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

    this.refresh.setOnClickListener {
      this.refresh.isEnabled = false
      this.accountRegistry.refresh()
    }

    return layout
  }

  override fun onStart() {
    super.onStart()

    this.accountRegistrySubscription =
      this.accountRegistry.events.subscribe(this::onAccountRegistryEvent)

    this.accountCreationSubscription =
      this.profilesController
        .accountEvents()
        .subscribe(this::onAccountEvent)

    this.reconfigureViewForRegistryStatus(this.accountRegistry.status)
    this.accountRegistry.refresh()
  }

  private fun onAccountRegistryEvent(event: AccountProviderRegistryEvent) {
    return when (event) {
      AccountProviderRegistryEvent.StatusChanged -> {
        this.uiThread.runOnUIThread(Runnable {
          this.reconfigureViewForRegistryStatus(this.accountRegistry.status)
        })
      }
      is AccountProviderRegistryEvent.Updated -> {

      }
      is AccountProviderRegistryEvent.SourceFailed -> {

      }
    }
  }

  @UiThread
  private fun reconfigureViewForRegistryStatus(status: AccountProviderRegistryStatus) {
    this.uiThread.checkIsUIThread()

    return when (status) {
      AccountProviderRegistryStatus.Idle -> {
        this.title.setText(R.string.settingsAccountRegistrySelect)
        this.accountList.visibility = View.VISIBLE
        this.progress.visibility = View.INVISIBLE
        this.refresh.isEnabled = true

        val availableDescriptions =
          this.determineAvailableAccountProviderDescriptions()

        if (availableDescriptions.isEmpty()) {
          this.progressText.visibility = View.VISIBLE
          this.progressText.setText(R.string.settingsAccountRegistryEmpty)
        } else {
          this.progressText.visibility = View.INVISIBLE
        }

        this.accountListData.clear()
        this.accountListData.addAll(availableDescriptions)
        this.accountListAdapter.notifyDataSetChanged()
      }

      AccountProviderRegistryStatus.Refreshing -> {
        this.title.setText(R.string.settingsAccountRegistrySelect)
        this.accountList.visibility = View.INVISIBLE
        this.progress.visibility = View.VISIBLE
        this.progressText.visibility = View.VISIBLE
        this.progressText.setText(R.string.settingsAccountRegistryRetrieving)
        this.refresh.isEnabled = false
      }
    }
  }

  override fun onStop() {
    super.onStop()

    this.accountCreationSubscription?.unsubscribe()
    this.accountRegistrySubscription?.unsubscribe()
  }

}
