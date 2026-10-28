package local.accountvault.android

import java.util.concurrent.Executor

class SyncTaskRunner(private val executor: Executor) {
    fun execute(task: () -> Unit, onComplete: (Throwable?) -> Unit) {
        executor.execute {
            var failure: Throwable? = null
            try {
                task()
            } catch (error: Throwable) {
                failure = error
            } finally {
                onComplete(failure)
            }
        }
    }
}
