import os
import github
import helpers

def publish_stable(workspace_dir, version_code: helpers.VersionCode):
    print(f"Publishing stable release")
    
    latest_release_commit_hash = github.get_latest_release_commit_hash('K1rakishou/Kuroba-Experimental')
    if (len(latest_release_commit_hash) == 0):
         print("Failed to get latest release commit hash.")
         exit(-1)

    commits = helpers.get_commits_since(latest_release_commit_hash)
    tag_name = f'v{version_code.major}.{version_code.minor}.{version_code.patch}-release'
    
    print(f'tag_name: {tag_name}')
    print(f'commits:\n{commits}')

    release_name = f'KurobaEx release {tag_name}'
    body = commits
    asset_path = workspace_dir + "/Kuroba/app/build/outputs/apk/stable/release/KurobaEx.apk"
    
    token = os.getenv('PAT') or ""
    if (len(token) == 0):
        print("Token is empty.")
        exit(-1)

    github.create_github_release(token, helpers.StableRepoName, tag_name, release_name, body, asset_path)

