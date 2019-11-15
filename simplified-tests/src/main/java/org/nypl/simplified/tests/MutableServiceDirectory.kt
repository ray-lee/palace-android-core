package org.nypl.simplified.tests

import com.google.common.base.Preconditions
import org.librarysimplified.services.api.ServiceDirectoryType
import org.slf4j.LoggerFactory

class MutableServiceDirectory : ServiceDirectoryType {

  private val logger = LoggerFactory.getLogger(MutableServiceDirectory::class.java)
  private val servicesLock = Object()
  private val services = HashMap<Class<*>, List<Any>>()

  override fun <T : Any> optionalServices(serviceClass: Class<T>): List<T> {
    return synchronized(this.servicesLock) {
      this.services[serviceClass] as List<T>? ?: listOf()
    }
  }

  fun clear() {
    this.logger.debug("clearing services")
    synchronized(this.servicesLock) {
      this.services.clear()
    }
  }

  fun <T : Any> putService(
    interfaces: List<Class<T>>,
    service: T
  ) {
    Preconditions.checkArgument(
      interfaces.isNotEmpty(),
      "Must supply at least one interface type")

    this.logger.debug("registering (replacing) service {}", service.javaClass.canonicalName)
    synchronized(this.servicesLock) {
      for (inter in interfaces) {
        this.services[inter] = listOf(service)
      }
    }
  }

  fun <T : Any> putService(
    interfaceType: Class<T>,
    service: T
  ) = this.putService(listOf(interfaceType), service)

  fun <T : Any> publishService(
    interfaces: List<Class<T>>,
    service: T
  ) {
    Preconditions.checkArgument(
      interfaces.isNotEmpty(),
      "Must supply at least one interface type")

    this.logger.debug("registering service {}", service.javaClass.canonicalName)
    synchronized(this.servicesLock) {
      for (inter in interfaces) {
        val existing: List<Any> = this.services[inter] ?: listOf()
        this.services[inter] = existing.plus(service)
      }
    }
  }

  fun <T : Any> publishService(
    interfaceType: Class<T>,
    service: T
  ) = this.publishService(listOf(interfaceType), service)

}
