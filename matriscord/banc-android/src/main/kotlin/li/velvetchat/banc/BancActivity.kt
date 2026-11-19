package li.velvetchat.banc

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BancActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var output: TextView
    private lateinit var runButton: Button
    private lateinit var pushButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        output = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextIsSelectable(true)
            setPadding(24, 24, 24, 24)
        }

        runButton = Button(this).apply {
            text = "Relancer le banc"
            setOnClickListener { runSpike() }
        }

        val resetButton = Button(this).apply {
            text = "Effacer la session enregistrée"
            setOnClickListener {
                SessionStore(this@BancActivity).clear()
                append("→ session effacée, le prochain lancement repassera par le login")
            }
        }

        pushButton = Button(this).apply {
            text = "S0.5 — configurer le push chiffré"
            setOnClickListener {
                output.text = ""
                scope.launch {
                    try {
                        S05Spike(applicationContext).configure { append(it) }
                    } catch (t: Throwable) {
                        Log.e(UnifiedPush.TAG, "S0.5 a échoué", t)
                        append("✗ ÉCHEC : ${t::class.qualifiedName}")
                        append("  ${t.message}")
                    }
                }
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@BancActivity).apply {
                text = "Velvet — banc S0.1"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 8)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(runButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(pushButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(resetButton, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(
                ScrollView(this@BancActivity).apply { addView(output) },
                LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f),
            )
        })

        if (intent?.getBooleanExtra("s05", false) == true) {
            pushButton.performClick()
        } else {
            runSpike()
        }
    }

    private fun runSpike() {
        output.text = ""
        runButton.isEnabled = false
        scope.launch {
            try {
                S01Spike(applicationContext).run { line -> append(line) }
            } catch (t: Throwable) {
                Log.e("MatriscordS01", "S0.1 a échoué", t)
                append("")
                append("✗ ÉCHEC : ${t::class.qualifiedName}")
                append("  ${t.message}")
                append("")
                append("  Stacktrace complète : adb logcat -s MatriscordS01")
            } finally {
                withContext(Dispatchers.Main) { runButton.isEnabled = true }
            }
        }
    }

    private fun append(line: String) {
        runOnUiThread {
            output.append(line)
            output.append("\n")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
