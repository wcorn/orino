module "longhorn_backup" {
  source = "./modules/s3"

  bucket_name = "orino-longhorn-backup"
  aws_region  = var.aws_region
}
