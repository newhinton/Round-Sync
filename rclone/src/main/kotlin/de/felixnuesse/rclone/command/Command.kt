package de.felixnuesse.rclone.command

interface Command<T> {

    fun exec(): T

}