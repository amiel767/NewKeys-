package androidx.compose.material.icons.outlined

// Genere par tools/aetherfx/gen_ae_icons.py — ne pas editer a la main.
// Icones Material officielles (Apache-2.0).  Reimplementees pour eviter la
// dependance `material-icons-extended` (11 398 classes, une par icone) dont
// 1 seulement sont utilisees.

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _ae_tune_f: ImageVector? = null

val Icons.Outlined.Tune: ImageVector
  get() = _ae_tune_f ?: ImageVector.Builder(
    name = "AeTune", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
  ).apply {
    path(fill = SolidColor(Color.Black)) {
  moveTo(3.0f, 17.0f)
  verticalLineTo(19.0f)
  horizontalLineTo(9.0f)
  verticalLineTo(17.0f)
  horizontalLineTo(3.0f)
  close()
  close()
  close()
  verticalLineTo(19.0f)
  horizontalLineTo(13.0f)
  verticalLineTo(5.0f)
  horizontalLineTo(3.0f)
  close()
  close()
  close()
  verticalLineTo(15.0f)
  horizontalLineTo(11.0f)
  verticalLineTo(13.0f)
  horizontalLineTo(3.0f)
  verticalLineTo(11.0f)
  horizontalLineTo(1.0f)
  verticalLineTo(17.0f)
  horizontalLineTo(3.0f)
  close()
  close()
  close()
  verticalLineTo(19.0f)
  horizontalLineTo(3.0f)
  verticalLineTo(21.0f)
  horizontalLineTo(7.0f)
  verticalLineTo(23.0f)
  horizontalLineTo(9.0f)
  verticalLineTo(9.0f)
  horizontalLineTo(7.0f)
  close()
  close()
  close()
  verticalLineTo(15.0f)
  horizontalLineTo(11.0f)
  verticalLineTo(17.0f)
  horizontalLineTo(21.0f)
  close()
  close()
  close()
  horizontalLineTo(5.0f)
  verticalLineTo(7.0f)
  horizontalLineTo(9.0f)
  verticalLineTo(5.0f)
  horizontalLineTo(5.0f)
  verticalLineTo(3.0f)
  horizontalLineTo(3.0f)
  verticalLineTo(9.0f)
  close()
    }
  }.build().also { _ae_tune_f = it }
