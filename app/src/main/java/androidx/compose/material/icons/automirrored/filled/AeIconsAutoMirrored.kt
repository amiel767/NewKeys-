package androidx.compose.material.icons.automirrored.filled

// Genere par tools/aetherfx/gen_ae_icons.py — ne pas editer a la main.
// Icones Material officielles (Apache-2.0).  Reimplementees pour eviter la
// dependance `material-icons-extended` (11 398 classes, une par icone) dont
// 3 seulement sont utilisees.

import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private var _ae_sort_m: ImageVector? = null
private var _ae_undo_m: ImageVector? = null
private var _ae_redo_m: ImageVector? = null

val Icons.AutoMirrored.Filled.Sort: ImageVector
  get() = _ae_sort_m ?: ImageVector.Builder(
    name = "AeSort", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
  ).apply {
    path(fill = SolidColor(Color.Black)) {
  moveTo(21.0f, 18.0f)
  horizontalLineTo(15.0f)
  verticalLineTo(16.0f)
  horizontalLineTo(21.0f)
  verticalLineTo(18.0f)
  close()
  close()
  close()
  verticalLineTo(20.0f)
  horizontalLineTo(3.0f)
  verticalLineTo(6.0f)
  horizontalLineTo(21.0f)
  close()
  close()
  close()
  horizontalLineTo(9.0f)
  verticalLineTo(16.0f)
  horizontalLineTo(21.0f)
  verticalLineTo(18.0f)
  close()
    }
  }.build().also { _ae_sort_m = it }

val Icons.AutoMirrored.Filled.Undo: ImageVector
  get() = _ae_undo_m ?: ImageVector.Builder(
    name = "AeUndo", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
  ).apply {
    path(fill = SolidColor(Color.Black)) {
  moveTo(11.5f, 8.0f)
  curveTo(14.15f, 8.0f, 16.55f, 8.99f, 18.4f, 10.6f)
  lineTo(22.0f, 7.0f)
  verticalLineTo(16.0f)
  horizontalLineTo(13.0f)
  lineTo(16.62f, 12.379999999999999f)
  curveTo(15.23f, 11.219999999999999f, 13.46f, 10.5f, 11.5f, 10.5f)
  curveTo(7.960000000000001f, 10.5f, 4.949999999999999f, 12.81f, 3.8999999999999986f, 16.0f)
  lineTo(1.5299999999999976f, 15.22f)
  curveTo(2.9200000000000017f, 11.03f, 6.850000000000001f, 8.0f, 11.5f, 8.0f)
  close()
    }
  }.build().also { _ae_undo_m = it }

val Icons.AutoMirrored.Filled.Redo: ImageVector
  get() = _ae_redo_m ?: ImageVector.Builder(
    name = "AeRedo", defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
  ).apply {
    path(fill = SolidColor(Color.Black)) {
  moveTo(5.600000000000001f, 10.6f)
  curveTo(7.449999999999999f, 8.99f, 9.85f, 8.0f, 12.5f, 8.0f)
  curveTo(17.15f, 8.0f, 21.08f, 11.03f, 22.46f, 15.219999999999999f)
  lineTo(20.1f, 16.0f)
  curveTo(18.15f, 16.0f, 16.37f, 16.72f, 14.98f, 17.88f)
  lineTo(11.0f, 16.0f)
  horizontalLineTo(2.0f)
  verticalLineTo(7.0f)
  lineTo(5.600000000000001f, 10.6f)
  close()
    }
  }.build().also { _ae_redo_m = it }
