# Changelog
This is the changelog for *casual caller* and all changes are listed in this document.

## [3.3.1-test-gh-action-2] - 2025-03-28

### feat: test again ([#43](https://github.com/casualcore/casual-caller/issues/43))
Nice test

With a list:
* Thing [#3](https://github.com/casualcore/casual-caller/issues/3) 
* Not an issue

Lovely!

## [3.3.1-test-gh-action-1] - 2025-03-28

### feat: test gh actions ([#42](https://github.com/casualcore/casual-caller/issues/42))
Testing test [#3](https://github.com/casualcore/casual-caller/issues/3)


## [3.2.23] - 2024-12-09
### Feature/3.2/http routing ([#40](https://github.com/casualcore/casual-caller/issues/40))

Makes it possible to define http service mappings for which tpcall will issue a http call instead.
This is something that can be preferable in a cloud environment for non transactional calls.

## [3.2.22] - 2024-10-29
### NOP ctor needed for CDI ([#39](https://github.com/casualcore/casual-caller/issues/39))



## [3.2.21] - 2024-10-08
### Feature/3.2/java 17 sonar fixes ([#36](https://github.com/casualcore/casual-caller/issues/36))

Java 17 sonar fixes

## [3.2.20] - 2024-10-07
### Feature/3.2/execution sticky ([#35](https://github.com/casualcore/casual-caller/issues/35))

When sticky is being used, tp(a)calls in the same transaction will use the same execution.

This will help with traceability.

## [3.2.19] - 2024-09-05
### Bugfix/3.2/handle topology update ([#34](https://github.com/casualcore/casual-caller/issues/34))

Do not schedule any new domain discovery while one is already running
If we get an incoming topology update while domain discovery is running, make note of it and schedule it after the domain discovery has finished. Also keep the lock for not issuing new domain discoveries while a domain discovery is running.
This is so that we use as little resources as possible when handling topology updates.

## [3.2.18] - 2024-04-09
### Feature/3.2/sphinx docs ([#30](https://github.com/casualcore/casual-caller/issues/30))

Sphinx docs.
We get the markdown in a nice way and we also get a javadoc link.
When we add more markdown documents they will automatically be available to add to documentation.

## [3.2.17] - 2024-04-08
### Feature/3.2/timer service non overlapping ([#28](https://github.com/casualcore/casual-caller/issues/28))

* in case discovery takes longer time than the timeout duration, the timer task should not overlap
* sonar bump
* make sure that unit tests are always run and that code coverage is also measured

## [3.2.16] - 2023-11-21
### tpacall signature change due to handling TPNOREPLY correctly in casual-jca ([#27](https://github.com/casualcore/casual-caller/issues/27))

tpacall signature change due to handling TPNOREPLY correctly in casual jca

## [3.2.14] - 2023-09-04
### feature/3.2/handle topology updates

When being informed of a topology update for a domain we issue a new domain discovery, using the known world, for that domain.

## [3.2.13] - 2023-08-29
### fixing sticky and failover

Fixing issues where calls fail when a sticky is present and an attempt to use another pool is made (because the stickied pool does not handle the requested service for instance. There was also a time window between picking a sticky pool and actually calling the pool and then setting it as sticky where multiple asynchronous calls in the same transaction could try to set the sticky (setting sticky when already set not allowed)

## [3.2.10] - 2023-06-13
### feature/3.2/handle domain disconnect exception ([#11](https://github.com/casualcore/casual-caller/issues/11))

* handle domain disconnect exeception ([#10](https://github.com/casualcore/casual-caller/issues/10))

This feature goes along with version 2.2.22 of casual-jca.

* A connection can throw DomainDisconnectedException and we handle it exactly the same way as we currently handle ResourceExceptions during tp(a)call.

* Removed data structure not really used, spoke to Tobias and he thinks it is a remnant of something

## [3.2.9] - 2023-05-24
### bugfixes/queue-cache-and-better-logging-on-validation-failure

* check if queue cache is empty
* log stack trace if validation fails
* guard against usage of Arrays.asList

## [3.2.8] - 2023-04-11
### need publishing section for ear ([#1](https://github.com/casualcore/casual-caller/issues/1))

Application needs ear publishing section

## [3.2.7] - 2023-04-05
### Jakarta EE



## [2.2.34] - 2024-10-10
### Feature/2.2/execution sticky ([#35](https://github.com/casualcore/casual-caller/issues/35)) ([#38](https://github.com/casualcore/casual-caller/issues/38))

When sticky is being used, tp(a)calls in the same transaction will use the same execution.

This will help with traceability.

## [2.2.19] - 2024-09-02
### Bugfix/2.2/handle topology update

Do not schedule any new domain discovery while one is already running
If we get an incoming topology update while domain discovery is running, make note of it and schedule it after the domain discovery has finished. Also keep the lock for not issuing new domain discoveries while a domain discovery is running.
This is so that we use as little resources as possible when handling topology updates.

Issue [#32](https://github.com/casualcore/casual-caller/issues/32)

## [2.2.18] - 2024-04-08
### Feature/2.2/timer service non overlapping ([#29](https://github.com/casualcore/casual-caller/issues/29))

* in case discovery takes longer time than the timeout duration, the timer task should not overlap
* sonar bump
* make sure that unit tests are always run and that code coverage is also measured
* spock bump
* latest sonar version that can be used with java 8 binaries

## [2.2.17] - 2023-11-20
### buggfix/2.2/tpacall tpnoreply signature change

tpacall signature change due to handling TPNOREPLY correctly in casual-jca

## [2.2.16] - 2023-09-14
### bugfix/2.2/discoveries non transactional

Any discovery should always be non transactional.

## [2.2.15] - 2023-09-04
### use the right import



## [2.2.14] - 2023-09-04
### Feature/2.2/handle topology updates

When being informed of a topology update for a domain we issue a new domain discovery, using the known world, for that domain.

## [2.2.13] - 2023-08-28
### fixing sticky and failover

Fixing issues where calls fail when a sticky is present and an attempt to use another pool is made (because the stickied pool does not handle the requested service for instance. There was also a time window between picking a sticky pool and actually calling the pool and then setting it as sticky where multiple asynchronous calls in the same transaction could try to set the sticky (setting sticky when already set not allowed)

## [2.2.10] - 2023-06-12
### handle domain disconnect exeception ([#10](https://github.com/casualcore/casual-caller/issues/10))

This feature goes along with version 2.2.22 of casual-jca.

* A connection can throw DomainDisconnectedException and we handle it exactly the same way as we currently handle ResourceExceptions during tp(a)call.

* Removed data structure not really used, spoke to Tobias and he thinks it is a remnant of something

## [2.2.9] - 2023-05-24
### bugfixes/queue-cache-and-better-logging-on-validation-failure

* check if queue cache is empty
* log stack trace if validation fails
* guard against usage of Arrays.asList

## [2.2.8] - 2023-04-11
### need publishing section for ear ([#2](https://github.com/casualcore/casual-caller/issues/2))

Application needs ear publishing section

## [2.2.7] - 2023-04-03
### initial



