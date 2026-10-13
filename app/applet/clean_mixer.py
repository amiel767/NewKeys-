path = "app/src/main/java/com/soundstage/mixer/ui/MixerScreen.kt"
with open(path, "r") as f:
    content = f.read()

first_idx = content.find("AppScreenPage.SETLISTS -> {")
if first_idx != -1:
    second_idx = content.find("AppScreenPage.SETLISTS -> {", first_idx + 1)
    if second_idx != -1:
        popup_idx = content.find("        when (uiState.activePopup) {", second_idx)
        if popup_idx != -1:
            content = content[:second_idx] + content[popup_idx:]

with open(path, "w") as f:
    f.write(content)
print("Cleaned up duplicate SetlistsScreen block successfully.")
