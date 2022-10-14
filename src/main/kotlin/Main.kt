import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import dto.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import okio.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


//делаю клиента для сетевого вызова
private val client = OkHttpClient.Builder()
    //запросы посылаю  с задержкой 30 секунд
    .connectTimeout(30, TimeUnit.SECONDS)
    // перехватчики - это подключаемые Java-компоненты, которые мы можем использовать для перехвата и обработки
    // запросов до их отправки в код нашего приложения.Кроме того, они предоставляют нам мощный механизм для обработки
    // ответа сервера до того, как контейнер отправит ответ обратно клиенту.
    .addInterceptor(HttpLoggingInterceptor().apply {
        //журнал уровня Body для перехвата информаницц по мегу запросу и ответу
        level = HttpLoggingInterceptor.Level.BODY
    })
    .build()

//Делаю json для приняия в нужном формате
private val gson = Gson()

//адрес эмулятора
private const val BASE_URL = "http://127.0.0.1:9999"

//функция, которая может быть приостановленас параметром строки url и TypeToken<T>, который сообщает Gson в какой
// виде я хочу преоброзовать строку
suspend fun <T> makeNewCall(url: String, typeToken: TypeToken<T>): T =

// suspendCoroutine это функция-конструктор, которая в основном используется для преобразования обратных вызовов в suspendфункции
    suspendCoroutine {
        //"продолжение" использую интерфес continuation
            continuation ->
        //делаю запрос
        Request.Builder()
            //адрес
            .url(url)
            //собрать
            .build()
            //выполнить новое обращение к клиенту
            .let(client::newCall)
//вызываем метод enqueue() (для асинхронного вызова) и создаём для него Callback. Запрос будет выполнен в отдельном потоке
//Идея коллбэков (обратных вызовов, ориг.: callbacks) состоит в том, чтобы передать одну функцию в качестве параметра
// другой функции и вызвать ее после завершения процесса.
            .enqueue(object : Callback {
                //для удачного вызова
                override fun onResponse(call: Call, response: Response) {
                    try {
                        //resume, которая  возобновляет выполнение корутины с последней точки остановки.
                        //При это выдаст  результат с последней точки остановки.
                        continuation.resume(gson.fromJson(response.body?.string(), typeToken.type))
                    } catch (e: JsonParseException) {
                        continuation.resumeWithException(e)
                    }
                }

                //неудачный вызов
                override fun onFailure(call: Call, e: IOException) {
                    //resumeWithException возобновляет
                    // выполнение корутины с последней точки остановки. При это выдаст исключение
                    continuation.resumeWithException(e)
                }
            })
    }
//функция получения постов с выходом списка постов
suspend fun getPosts(): List<Post> =
    //сделать запрос по адресу и получить список постов
    makeNewCall("$BASE_URL/api/slow/posts", object : TypeToken<List<Post>>() {})

//функция получения комментариев с выходом списка комментариев
suspend fun getComments(postId: Long): List<Comment> =
    //сделать запрос по адресу из поста с таким-то id и получить список комментариев
    makeNewCall("$BASE_URL/api/slow/posts/$postId/comments", object : TypeToken<List<Comment>>() {})
//тоже с авторами
suspend fun getAuthors(id: Long): List<Author> =
    makeNewCall("$BASE_URL/api/slow/authors/$id", object : TypeToken<List<Author>>() {})

fun main() {
    //Функция runBlocking блокирует вызывающий поток, пока все корутины внутри вызова runBlocking {...} не завершат свое выполнение.
    runBlocking {
        val posts = getPosts()

        val result = posts.map {
            async {
                PostAndComments(it,
                    getAuthors(it.authorId),
                    getComments(it.id).map { comment ->
                        CommentsAndAuthor(comment, getAuthors(comment.authorId))
                    })
            }
        }.awaitAll()

        println(result)
    }

}