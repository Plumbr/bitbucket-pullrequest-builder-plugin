package bitbucketpullrequestbuilder.bitbucketpullrequestbuilder;

import bitbucketpullrequestbuilder.bitbucketpullrequestbuilder.bitbucket.BuildState;
import hudson.model.Result;
import hudson.model.Run;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Created by nishio
 */
public class BitbucketBuilds {
  private static final Logger logger = Logger.getLogger(BitbucketBuilds.class.getName());
  private BitbucketBuildTrigger trigger;
  private BitbucketRepository repository;

  public BitbucketBuilds(BitbucketBuildTrigger trigger, BitbucketRepository repository) {
    this.trigger = trigger;
    this.repository = repository;
  }

  void onStarted(BitbucketCause cause, Run<?, ?> build) {
    if (cause == null) {
      return;
    }
    try {
      build.setDescription(cause.getShortDescription());
      String buildUrl = getBuildUrl(build.getUrl());
      repository.setBuildStatus(cause, BuildState.INPROGRESS, buildUrl);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Can't update build description", e);
    }
  }

  void onCompleted(BitbucketCause cause, Result result, String buildUrl) {
    if (cause == null) {
      return;
    }

    String fullBuildUrl = getBuildUrl(buildUrl);
    BuildState state = result == Result.SUCCESS ? BuildState.SUCCESSFUL : BuildState.FAILED;
    repository.setBuildStatus(cause, state, fullBuildUrl);

    if (this.trigger.getApproveIfSuccess() && result == Result.SUCCESS) {
      this.repository.postPullRequestApproval(cause.getPullRequestId());
    }
  }

  private Jenkins getInstance() {
    final Jenkins instance = Jenkins.getInstance();
    if (instance == null) {
      throw new IllegalStateException("Jenkins instance is NULL!");
    }
    return instance;
  }

  private String getBuildUrl(String buildUrl) {
    String rootUrl = getInstance().getRootUrl();
    if (rootUrl == null || "".equals(rootUrl)) {
      logger.log(Level.WARNING, "Jenkins Root URL is empty, please set it on Global Configuration");
      return "";
    }
    return rootUrl + buildUrl;
  }
}
