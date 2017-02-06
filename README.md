# saved-for-reddit

A webapp to manage and search through all your saved reddit posts

## Overview

Reddit allows its users to save posts, however does not allow users to search through these saved posts or filter them based on subreddits.
```saved-for-reddit``` is a Single Page Application (SPA) written in Clojurescript to manage and search through all your saved reddit posts.

This is a javascript application that runs completely in the users browser, no user details are sent to any external servers. Reddit authentication is done using OAuth2 access flow provided by the Reddit API. The reddit API access is only requested for an hour.

## Features

* All saved posts and comments are retreived from Reddit and displayed in a single page
* All subreddits with saved posts are displayed, with a post count per subreddit
* Saved posts can be filtered by subreddits
* Saved posts can be unsaved
* Post thumbnails are displayed (if they are provided by the Reddit API)
* NSFW posts will be marked accordingly
* If your reddit preference is set to not display NSFW thumnails, then a placeholder NSFW thumnail will be displayed

## TODO

* fix search bar to filter out posts. Currently the browsers find should to be used to search for keyywords amongst your saved posts.
* better error processing
    * try to refresh api token first if 401 is received
* thumbnail image requests are not done over SSL.

## License

Distributed under the MIT License.
