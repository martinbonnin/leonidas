terraform {
  backend "gcs" {
    bucket = "leonidas-tfstate" // created manually
    prefix = "terraform/state"
  }
}

variable "region" {
  default = "europe-west9"
}

variable "project" {
  default = "leonidas-415019"
}

provider google-beta {
  project = var.project
  region  = var.region
}

resource "google_project_service" "artifactregistry" {
  provider = google-beta
  service  = "artifactregistry.googleapis.com"

  timeouts {
    create = "2m"
    update = "2m"
  }

  disable_dependent_services = true
}

resource "google_project_service" "cloud-run-admin" {
  provider = google-beta
  service  = "run.googleapis.com"

  timeouts {
    create = "2m"
    update = "2m"
  }

  disable_dependent_services = true
}

resource "google_artifact_registry_repository" "repository" {
  provider = google-beta
  repository_id = "leonidas-images"
  description   = "leonidas images"
  format        = "DOCKER"
}

resource "google_cloud_run_v2_service" "leonidas" {
  name     = "leonidas"
  provider = google-beta
  ingress  = "INGRESS_TRAFFIC_ALL"
  location = var.region

  template {
    containers {
      image = "europe-west9-docker.pkg.dev/leonidas-415019/leonidas-images/leonidas"
      resources {
        cpu_idle = true
        startup_cpu_boost = true
      }
    }
  }
  timeouts {
    create = "60m"
  }
}

resource "google_cloud_run_service_iam_binding" "default" {
  provider = google-beta
  location = google_cloud_run_v2_service.leonidas.location
  service  = google_cloud_run_v2_service.leonidas.name
  role     = "roles/run.invoker"
  members = [
    "allUsers"
  ]
}

#
# module "gh_oidc" {
#   source      = "terraform-google-modules/github-actions-runners/google//modules/gh-oidc"
#   project_id  = var.project_id
#   pool_id     = "example-pool"
#   provider_id = "example-gh-provider"
#   sa_mapping = {
#     "foo-service-account" = {
#       sa_name   = "projects/my-project/serviceAccounts/foo-service-account@my-project.iam.gserviceaccount.com"
#       attribute = "attribute.repository/${USER/ORG}/<repo>"
#     }
#   }
# }