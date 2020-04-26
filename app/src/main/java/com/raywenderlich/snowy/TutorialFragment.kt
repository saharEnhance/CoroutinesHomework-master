/*
 * Copyright (c) 2019 Razeware LLC
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 *  distribute, sublicense, create a derivative work, and/or sell copies of the
 *  Software in any work that is designed, intended, or marketed for pedagogical or
 *  instructional purposes related to programming, coding, application development,
 *  or information technology.  Permission for such use, copying, modification,
 *  merger, publication, distribution, sublicensing, creation of derivative works,
 *  or sale is expressly withheld.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */

package com.raywenderlich.snowy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.raywenderlich.snowy.model.Tutorial
import com.raywenderlich.snowy.utils.SnowFilter
import kotlinx.android.synthetic.main.fragment_tutorial.*
import kotlinx.coroutines.*
import java.net.URL

/***
 * For example, when you call await(), the system suspends
 * the outer coroutine until there is a value present.
 * Once the value is there, it uses the continuation,
 * to return it back to the outer coroutine. This way, it
 * doesn’t have to block threads, it can just notify itself
 * that a coroutine needs a thread to continue its work.
 */
class TutorialFragment : Fragment() {

  companion object {

    const val TUTORIAL_KEY = "TUTORIAL"

    fun newInstance(tutorial: Tutorial): TutorialFragment {
      val fragmentHome = TutorialFragment()
      val args = Bundle()
      args.putParcelable(TUTORIAL_KEY, tutorial)
      fragmentHome.arguments = args
      return fragmentHome
    }
  }

  /***
   * Once you cancel a Job, you cannot reuse it for coroutines.
   * You have to create a new one. This is why it’s a good practice to
   * either avoid adding Jobs to the CoroutineContext of your scope, or
   * to recreate jobs according to your app’s lifecycle.
   */
  private val parentJob = Job()
  /***
   * This creates a CoroutineExceptionHandler to log exceptions.
   * Additionally, it creates a coroutine on the main thread to
   * show error messages on the UI.
   * You also log your exceptions in a separate coroutine,
   * which will live with your app’s lifecycle.
   * This is useful if you need to log your exceptions to tools
   * like Crashlytics.
   */
  private val coroutineExceptionHandler: CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
      //2
      coroutineScope.launch(Dispatchers.Main) {
        //3
        errorMessage.visibility = View.VISIBLE
        errorMessage.text = getString(R.string.error_message)
      }

      GlobalScope.launch { println("Caught $throwable") }
    }

  private val coroutineScope = CoroutineScope(Dispatchers.Main + parentJob +
          coroutineExceptionHandler)

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                            savedInstanceState: Bundle?): View? {
    val tutorial = arguments?.getParcelable(TUTORIAL_KEY) as Tutorial
    val view = inflater.inflate(R.layout.fragment_tutorial, container, false)
    view.findViewById<TextView>(R.id.tutorialName).text = tutorial.name
    view.findViewById<TextView>(R.id.tutorialDesc).text = tutorial.description
    return view
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    val tutorial = arguments?.getParcelable(TUTORIAL_KEY) as Tutorial

    /***
     * You launch a coroutine on the main
     * thread. But the originalBitmap is computed in a worker
     * thread pool, so it doesn’t freeze the UI. Once you call
     * await(), it will suspend launch(), until the image value
     * is returned.
     */
    coroutineScope.launch(Dispatchers.Main) {
//      val originalBitmap: Bitmap = getOriginalBitmapAsync(tutorial).await()
      val originalBitmap = getOriginalBitmapAsync2(tutorial)

      /***
       * You’re simply applying the filter to a loaded image,
       * and then passing it to loadImage().
       * That’s the beauty of coroutines: they help convert
       * your async operations into natural, sequential,
       * method calls.
       */
//      val snowFilterBitmap = loadSnowFiltersAsync(originalBitmap).await()
      val snowFilterBitmap = loadSnowFiltersAsync2(originalBitmap)
      loadImage(snowFilterBitmap)
    }
  }

  private fun loadImage(snowFilterBitmap: Bitmap) {
    progressBar.visibility = View.GONE
    snowFilterImage?.setImageBitmap(snowFilterBitmap)
  }

  /**
   * Creates a regular function, getOriginalBitmapAsync(),
   * which returns a Deferred Bitmap value. This emphasizes
   * that the result may not be immediately available.
   *
   * It is returning a value asynchronously.
   * But async() is better used if you have multiple requests.
   * It’s really useful for parallelism, as you can run a few operations,
   * without blocking or suspending, at the same time.
   */
  private fun getOriginalBitmapAsync(tutorial: Tutorial): Deferred<Bitmap> =
    /***
     * Use the async() to create a coroutine in an input-output
     * optimized Dispatcher. This will offload work from the main
     * thread, to avoid freezing the UI.
     */
    coroutineScope.async(Dispatchers.IO) {
      URL(tutorial.url).openStream().use {
        /***
         * Opens a stream from the image’s URL and uses it to
         * create a Bitmap, finally returning it.
         */
        return@async BitmapFactory.decodeStream(it)
      }
    }

  /***
   * Applying a filter is a heavy task because it has to
   * work pixel-by-pixel, for the entire image.
   * This is usually CPU intensive work, so you can use the
   * Default dispatcher to use a worker thread.
   */
  private fun loadSnowFiltersAsync(originalBitmap: Bitmap): Deferred<Bitmap> =
    coroutineScope.async(Dispatchers.Default) {
      SnowFilter.applySnowEffect(originalBitmap)
    }

  private suspend fun getOriginalBitmapAsync2(tutorial: Tutorial): Bitmap =
    withContext(Dispatchers.IO) {
      URL(tutorial.url).openStream().use {
        return@withContext BitmapFactory.decodeStream(it)
      }
    }

  private suspend fun loadSnowFiltersAsync2(originalBitmap: Bitmap): Bitmap =
    withContext(Dispatchers.Default) {
      SnowFilter.applySnowEffect(originalBitmap)
    }

  /***
   * clean up your coroutines, to avoid leaks.
   */
  override fun onDestroy() {
    super.onDestroy()
    parentJob.cancel()
  }
}
