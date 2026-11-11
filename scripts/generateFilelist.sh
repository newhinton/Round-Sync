#!/bin/bash
set -efu

if [ "$#" -ne 2 ]; then
    echo "Only pass the amount of commits and one translation file!"
    exit 1
fi

DIFF=$(git diff -U0 "HEAD~$1" -- "$2" | grep -E "^\+" | grep -v +++ | cut -c 2- | sed 's/^[ \t]*\(.*$\)/\1/' || true)
printf '<xml>%s</xml>\n' "$DIFF" | xmlstarlet sel -t -m '//string' -v . -n > changed_texts.txt
TRANSLATIONS=$(cat changed_texts.txt)


TRANSLATIONS="${TRANSLATIONS//$'\n'/' ; '}"
TRANSLATIONS="${TRANSLATIONS//$'\r'/' ; '}"

# First, replace all string-substitutions
TRANSLATIONS=$(printf '%s\n' "$TRANSLATIONS" | sed 's/%1$s//g; s/%d//g; s/%s//g; s/"//g')

printf '%s\n' "$TRANSLATIONS"
