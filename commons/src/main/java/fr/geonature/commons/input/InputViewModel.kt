package fr.geonature.commons.input

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.geonature.commons.input.io.InputJsonReader
import fr.geonature.commons.input.io.InputJsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * [AbstractInput] view model.
 *
 * @author [S. Grimault](mailto:sebastien.grimault@gmail.com)
 */
open class InputViewModel<I : AbstractInput>(application: Application,
                                             inputJsonReaderListener: InputJsonReader.OnInputJsonReaderListener<I>,
                                             inputJsonWriterListener: InputJsonWriter.OnInputJsonWriterListener<I>) : AndroidViewModel(application) {

    internal val inputManager = InputManager.getInstance(application,
                                                         inputJsonReaderListener,
                                                         inputJsonWriterListener)

    private var selectedInputToDelete: I? = null

    /**
     * Reads all [AbstractInput]s.
     */
    fun readInputs(): LiveData<List<I>> {
        viewModelScope.launch {
            inputManager.readInputs()
        }

        return inputManager.inputs
    }

    /**
     * Reads [AbstractInput] from given ID.
     *
     * @param id The [AbstractInput] ID to read. If omitted, read the current saved [AbstractInput].
     */
    fun readInput(id: Long? = null): LiveData<I> {
        viewModelScope.launch {
            inputManager.readInput(id)
        }

        return inputManager.input
    }

    /**
     * Reads the current [AbstractInput].
     */
    fun readCurrentInput(): LiveData<I> {
        return readInput()
    }

    /**
     * Saves the given [AbstractInput] and sets it as default current [AbstractInput].
     *
     * @param input the [AbstractInput] to save
     */
    fun saveInput(input: I) {
        GlobalScope.launch(Dispatchers.Main) {
            inputManager.saveInput(input)
        }
    }

    /**
     * Deletes [AbstractInput] from given ID.
     *
     * @param input the [AbstractInput] to delete
     */
    fun deleteInput(input: I) {
        GlobalScope.launch(Dispatchers.Main) {
            inputManager.deleteInput(input.id)
        }
    }

    /**
     * Exports [AbstractInput] from given ID as `JSON` file.
     *
     * @param id the [AbstractInput] ID to export
     */
    fun exportInput(id: Long) {
        GlobalScope.launch(Dispatchers.Main) {
            inputManager.exportInput(id)
        }
    }

    /**
     * Restores previously deleted [AbstractInput].
     */
    fun restoreDeletedInput() {
        val selectedInputToRestore = selectedInputToDelete ?: return

        viewModelScope.launch {
            inputManager.saveInput(selectedInputToRestore)
            selectedInputToDelete = null
        }
    }

    /**
     * Default Factory to use for [InputViewModel].
     *
     * @author [S. Grimault](mailto:sebastien.grimault@gmail.com)
     */
    class Factory<T : InputViewModel<I>, I : AbstractInput>(val creator: () -> T) : ViewModelProvider.Factory {
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST") return creator() as T
        }
    }
}