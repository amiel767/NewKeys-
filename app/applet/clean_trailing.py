path = "app/src/main/java/com/soundstage/mixer/ui/MixerScreen.kt"
with open(path, "r") as f:
    content = f.read()

idx = content.find("            AppScreenPage.SETLISTS -> {")
if idx != -1:
    popup_idx = content.find("        when (uiState.activePopup) {", idx)
    if popup_idx != -1:
        content = content[:idx] + content[popup_idx:]

with open(path, "w") as f:
    f.write(content)
print("Cleaned trailing AppScreenPage.SETLISTS block.")
